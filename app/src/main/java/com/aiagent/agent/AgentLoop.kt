package com.aiagent.agent

import android.graphics.Bitmap
import android.util.Log
import com.aiagent.config.ConfigManager
import com.aiagent.experience.ExperienceMatcher
import com.aiagent.experience.Reflector
import com.aiagent.llm.ChatRequest
import com.aiagent.llm.ContentPart
import com.aiagent.llm.ContentWrapper
import com.aiagent.llm.ImageUrl
import com.aiagent.llm.LLMClient
import com.aiagent.llm.Message
import com.aiagent.tools.ToolContext
import com.aiagent.tools.ToolRegistry
import com.aiagent.tools.bitmapToBase64
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Agent 核心闭环 — 参考 Codex 的 run_turn 模式
 */
class AgentLoop(
    private val llmClient: LLMClient,
    private val configManager: ConfigManager,
    private val toolRegistry: ToolRegistry,
    private val experienceMatcher: ExperienceMatcher,
    private val reflector: Reflector
) {

    companion object {
        private const val TAG = "AgentLoop"
        private const val MAX_ITERATIONS = 10
    }

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<AgentResult>(AgentResult.Thinking("就绪"))
    val state: StateFlow<AgentResult> = _state

    // 对话历史（线程安全）
    private val _history = mutableListOf<ChatMessage>()
    val conversationHistory: List<ChatMessage> get() = synchronized(_history) { _history.toList() }

    /**
     * 重置对话
     */
    fun resetConversation() {
        synchronized(_history) { _history.clear() }
        _state.value = AgentResult.Thinking("就绪")
    }

    /**
     * 配置变更时刷新 LLMClient 缓存
     */
    fun invalidateCache() {
        llmClient.invalidateCache()
    }

    /**
     * 执行一次完整的 Agent 闭环
     */
    suspend fun run(
        userText: String,
        image: Bitmap? = null
    ): AgentResult {
        return mutex.withLock {
            runInternal(userText, image)
        }
    }

    private suspend fun runInternal(
        userText: String,
        image: Bitmap?
    ): AgentResult {
        var messages = mutableListOf<Message>()
        var iteration = 0
        val toolCallLog = mutableListOf<String>()

        _state.value = AgentResult.Thinking("正在理解您的问题...")

        // 获取当前模型配置
        val currentModel = configManager.getConfig()?.model ?: "gpt-4o"

        // 构建系统指令
        val systemPrompt = buildSystemPrompt()

        // 检索相关经验
        val fewShotText = try {
            experienceMatcher.matchAsPrompt(userText)
        } catch (e: Exception) {
            Log.w(TAG, "经验检索失败", e)
            ""
        }
        val fullSystem = if (fewShotText.isNotEmpty()) {
            "$systemPrompt\n\n以下是一些相关的历史经验，可供参考：\n$fewShotText"
        } else {
            systemPrompt
        }
        messages.add(Message(role = "system", content = ContentWrapper(fullSystem)))

        // 追加历史对话（最近 10 轮）
        val historySnapshot = synchronized(_history) { _history.toList() }
        val historySlice = historySnapshot.takeLast(20)
        for (hist in historySlice) {
            when (hist.role) {
                "user" -> {
                    val content = if (hist.imageBase64 != null) {
                        ContentWrapper(listOf(
                            ContentPart(type = "text", text = hist.content),
                            ContentPart(type = "image_url", imageUrl = ImageUrl(hist.imageBase64))
                        ))
                    } else {
                        ContentWrapper(hist.content)
                    }
                    messages.add(Message(role = "user", content = content))
                }
                "assistant" -> {
                    messages.add(Message(role = "assistant", content = ContentWrapper(hist.content)))
                }
            }
        }

        // 构建当前用户消息（缓存 base64 避免重复编码）
        var cachedBase64: String? = null
        val userContent: ContentWrapper = if (image != null) {
            cachedBase64 = bitmapToBase64(image)
            ContentWrapper(listOf(
                ContentPart(type = "text", text = userText),
                ContentPart(type = "image_url", imageUrl = ImageUrl(cachedBase64))
            ))
        } else {
            ContentWrapper(userText)
        }
        messages.add(Message(role = "user", content = userContent))

        // Agent Loop
        while (iteration < MAX_ITERATIONS) {
            iteration++
            _state.value = AgentResult.Thinking(
                if (iteration == 1) "正在思考..." else "正在执行第 $iteration 步..."
            )

            val request = ChatRequest(
                model = currentModel,
                messages = messages,
                tools = toolRegistry.getDefinitions(),
                toolChoice = "auto"
            )

            val response = try {
                llmClient.chat(request)
            } catch (e: Exception) {
                Log.e(TAG, "LLM 调用失败", e)
                _state.value = AgentResult.Failure("LLM 调用失败: ${e.message}")
                return AgentResult.Failure("LLM 调用失败: ${e.message}")
            }

            val choice = response.choices?.firstOrNull()
                ?: return AgentResult.Failure("LLM 返回空响应")

            val msg = choice.message
                ?: return AgentResult.Failure("LLM 返回空消息")

            // 检查是否有工具调用
            if (!msg.toolCalls.isNullOrEmpty()) {
                messages.add(msg)

                for (call in msg.toolCalls) {
                    val toolName = call.function?.name ?: continue
                    _state.value = AgentResult.Thinking("正在执行工具: $toolName...")
                    toolCallLog.add(toolName)

                    val params = try {
                        @Suppress("UNCHECKED_CAST")
                        gson.fromJson(
                            call.function.arguments ?: "{}",
                            Map::class.java
                        ) as Map<String, Any>
                    } catch (e: Exception) {
                        Log.w(TAG, "工具参数解析失败: ${call.function.arguments}", e)
                        mapOf<String, Any>()
                    }

                    val toolResult = try {
                        toolRegistry.execute(
                            toolName,
                            params,
                            ToolContext(
                                configManager = configManager,
                                llmClient = llmClient
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "工具执行失败: $toolName", e)
                        "工具执行失败: ${e.message}"
                    }

                    messages.add(
                        Message(
                            role = "tool",
                            content = ContentWrapper(toolResult),
                            toolCallId = call.id,
                            name = toolName
                        )
                    )
                }
            } else {
                // 最终文本回答
                val finalContent = msg.textContent() ?: msg.content?.asString() ?: "无内容返回"

                // 保存到历史
                val userImageBase64 = if (image != null) cachedBase64 ?: bitmapToBase64(image) else null
                synchronized(_history) {
                    _history.add(ChatMessage(role = "user", content = userText, imageBase64 = userImageBase64))
                    _history.add(ChatMessage(role = "assistant", content = finalContent))
                }

                _state.value = AgentResult.Success(finalContent, toolCallLog)

                // 异步反思沉淀经验
                scope.launch {
                    try {
                        reflector.reflect(messages, userText, true)
                    } catch (e: Exception) {
                        Log.w(TAG, "反思沉淀失败", e)
                    }
                }

                return AgentResult.Success(finalContent, toolCallLog)
            }
        }

        _state.value = AgentResult.Failure("达到最大迭代次数 ($MAX_ITERATIONS)，任务未完成")
        return AgentResult.Failure("达到最大迭代次数 ($MAX_ITERATIONS)，任务未完成")
    }

    private fun buildSystemPrompt(): String = """
你是一个智能 AI 助手，运行在安卓设备上。你具备以下能力：
1. 理解和分析图片（analyze_image 工具）
2. 搜索互联网信息（web_search 工具）
3. 执行数学计算（calculate 工具）
4. 总结长文本（summarize 工具）

当用户的问题需要使用工具时，请主动调用相应工具。调用工具后，根据工具返回的结果给用户提供清晰的回答。

使用规则：
- 用中文与用户对话
- 回答简洁准确，直接给出结果
- 如果工具调用失败，告知用户并提供替代建议
- 不要重复调用同一工具
- 如果无法完成用户请求，诚实告知
    """.trimIndent()
}
