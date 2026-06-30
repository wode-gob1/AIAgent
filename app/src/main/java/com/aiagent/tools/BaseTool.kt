package com.aiagent.tools

import com.aiagent.config.ConfigManager
import com.aiagent.llm.FunctionDefinition
import com.aiagent.llm.LLMClient
import com.aiagent.llm.ToolDefinition

/**
 * 工具执行上下文，提供对配置和 LLM 客户端的访问
 */
data class ToolContext(
    val configManager: ConfigManager,
    val llmClient: LLMClient
)

/**
 * 所有工具的抽象基类
 */
abstract class BaseTool {
    abstract val name: String
    abstract val description: String
    abstract val parametersSchema: Map<String, Any>

    /**
     * 供 OpenAI Function Calling 使用的完整工具定义
     */
    fun toToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = parametersSchema
            )
        )
    }

    /**
     * 执行工具并返回结果字符串
     */
    abstract suspend fun execute(params: Map<String, Any>, context: ToolContext): String
}
