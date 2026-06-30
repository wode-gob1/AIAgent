package com.aiagent.tools

import com.aiagent.llm.ChatRequest
import com.aiagent.llm.ContentWrapper
import com.aiagent.llm.Message

/**
 * 文本摘要工具：调用 LLM 对长文本进行总结
 */
class SummarizeTool : BaseTool() {

    override val name = "summarize"
    override val description = "对长文本进行摘要总结。输入一段文字，返回简洁的摘要。"

    override val parametersSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "text" to mapOf(
                "type" to "string",
                "description" to "需要总结的文本内容"
            ),
            "max_sentences" to mapOf(
                "type" to "integer",
                "description" to "摘要的最大句子数（可选，默认3句）"
            )
        ),
        "required" to listOf("text")
    )

    override suspend fun execute(params: Map<String, Any>, context: ToolContext): String {
        val text = params["text"] as? String ?: return "错误：缺少文本内容"
        val maxSentences = (params["max_sentences"] as? Number)?.toInt() ?: 3

        if (text.length <= 100) {
            return "文本较短，无需摘要：$text"
        }

        val config = context.configManager.getConfig()
            ?: return "错误：API 未配置"

        return try {
            val systemMsg = "你是一个文本摘要专家。请用中文对以下文本进行简洁的摘要，不超过 $maxSentences 句话。只输出摘要内容。"
            val messages = listOf(
                Message(role = "system", content = ContentWrapper(systemMsg)),
                Message(role = "user", content = ContentWrapper(text))
            )
            val request = ChatRequest(
                model = config.model,
                messages = messages,
                tools = null,
                toolChoice = null
            )
            val response = context.llmClient.chat(request)
            response.choices?.firstOrNull()?.message?.textContent()
                ?: "摘要失败：未获得结果"
        } catch (e: Exception) {
            "摘要失败: ${e.message}"
        }
    }
}
