package com.aiagent.experience

import android.util.Log
import com.aiagent.config.ConfigManager
import com.aiagent.data.ExperienceDao
import com.aiagent.data.ExperienceEntity
import com.aiagent.llm.ChatRequest
import com.aiagent.llm.ContentWrapper
import com.aiagent.llm.LLMClient
import com.aiagent.llm.Message
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 反思器：任务成功后分析对话历史，提取可复用经验
 */
class Reflector(
    private val dao: ExperienceDao,
    private val llmClient: LLMClient,
    private val configManager: ConfigManager
) {
    private val gson = Gson()

    suspend fun reflect(conversation: List<Message>, userGoal: String, success: Boolean) {
        if (!success) return

        val config = configManager.getConfig() ?: return

        withContext(Dispatchers.IO) {
            try {
                // 简化对话
                val simplifiedConversation = conversation.mapNotNull { msg ->
                    when {
                        msg.role == "user" -> "用户: ${msg.textContent() ?: "[图片]"}"
                        msg.role == "assistant" -> "助手: ${msg.textContent() ?: "[无内容]"}"
                        msg.role == "tool" -> "[工具结果: ${msg.textContent()?.take(100) ?: ""}]"
                        else -> null
                    }
                }.joinToString("\n")

                val prompt = """
分析以下成功完成的对话，提取一个可复用的任务模板。

用户目标: $userGoal

对话记录:
$simplifiedConversation

请严格按以下 JSON 格式输出经验模板：
{
  "taskType": "简短的任务类型名称",
  "keywords": "关键词1,关键词2,关键词3",
  "steps": ["步骤1描述", "步骤2描述"],
  "tools": ["使用的工具名1", "使用的工具名2"],
  "examplePrompt": "经验模板：当用户要求{类似任务}时，处理步骤..."
}

注意：只输出 JSON。
                """.trimIndent()

                val messages = listOf(
                    Message(role = "system", content = ContentWrapper("你是一个经验提炼专家。只输出 JSON。")),
                    Message(role = "user", content = ContentWrapper(prompt))
                )

                val request = ChatRequest(
                    model = config.model,
                    messages = messages,
                    tools = null,
                    toolChoice = null
                )

                val response = llmClient.chat(request)
                val content = response.choices?.firstOrNull()?.message?.textContent()
                    ?: return@withContext

                val jsonStr = extractJson(content)
                val parsed = gson.fromJson(jsonStr, ExperienceData::class.java)

                val existing = dao.searchByType(parsed.taskType)
                if (existing.isNotEmpty()) {
                    val exp = existing.first()
                    dao.update(
                        exp.copy(
                            keywords = parsed.keywords,
                            steps = gson.toJson(parsed.steps),
                            tools = gson.toJson(parsed.tools),
                            examplePrompt = parsed.examplePrompt,
                            successCount = exp.successCount + 1,
                            lastUsedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    val entity = ExperienceEntity(
                        taskType = parsed.taskType,
                        keywords = parsed.keywords,
                        steps = gson.toJson(parsed.steps),
                        tools = gson.toJson(parsed.tools),
                        examplePrompt = parsed.examplePrompt,
                        successCount = 1
                    )
                    dao.insert(entity)
                }
                Log.d("Reflector", "经验沉淀成功: ${parsed.taskType}")
            } catch (e: Exception) {
                Log.w("Reflector", "反思失败", e)
            }
        }
    }

    private fun extractJson(text: String): String {
        val codeBlockRegex = Regex("```json\\s*([\\s\\S]*?)```")
        val match = codeBlockRegex.find(text)
        if (match != null) return match.groupValues[1].trim()

        val braceStart = text.indexOf('{')
        val braceEnd = text.lastIndexOf('}')
        if (braceStart >= 0 && braceEnd > braceStart) {
            return text.substring(braceStart, braceEnd + 1)
        }
        return text
    }

    data class ExperienceData(
        val taskType: String,
        val keywords: String,
        val steps: List<String>,
        val tools: List<String>,
        val examplePrompt: String
    )
}
