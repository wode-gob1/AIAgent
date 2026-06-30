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
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 经验解析器
 */
class ExperienceParser(
    private val dao: ExperienceDao,
    private val llmClient: LLMClient,
    private val configManager: ConfigManager
) {
    private val gson = Gson()

    suspend fun parseDemo(demoText: String): ExperienceParserResult {
        val config = configManager.getConfig()
            ?: return ExperienceParserResult.Error("API 未配置")

        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
请将以下任务示范解析为结构化 JSON 格式。

示范文本：
$demoText

请严格按以下 JSON 格式输出，不要添加额外文字：
{
  "taskType": "任务类型名称",
  "keywords": "关键词1,关键词2,关键词3",
  "steps": ["步骤1", "步骤2", "步骤3"],
  "tools": ["工具1", "工具2"],
  "examplePrompt": "当用户提出类似请求时，可以使用此模板：\n用户: {用户问题}\n助手: {回答模板，包含工具调用流程}"
}
                """.trimIndent()

                val messages = listOf(
                    Message(role = "system", content = ContentWrapper("你是一个经验解析专家。只输出 JSON。")),
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
                    ?: return@withContext ExperienceParserResult.Error("LLM 未返回有效内容")

                val jsonStr = extractJson(content)
                val parsed = gson.fromJson(jsonStr, ExperienceData::class.java)

                val entity = ExperienceEntity(
                    taskType = parsed.taskType,
                    keywords = parsed.keywords,
                    steps = gson.toJson(parsed.steps),
                    tools = gson.toJson(parsed.tools),
                    examplePrompt = parsed.examplePrompt
                )

                val id = dao.insert(entity)
                ExperienceParserResult.Success("经验解析成功，已保存为: ${parsed.taskType}")
            } catch (e: Exception) {
                Log.e("ExperienceParser", "解析失败", e)
                ExperienceParserResult.Error("解析失败: ${e.message}")
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

sealed class ExperienceParserResult {
    data class Success(val message: String) : ExperienceParserResult()
    data class Error(val message: String) : ExperienceParserResult()
}
