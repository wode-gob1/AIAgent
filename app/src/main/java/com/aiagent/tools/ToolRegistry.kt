package com.aiagent.tools

import com.aiagent.llm.ToolDefinition

/**
 * 工具注册中心，管理所有可用工具
 */
class ToolRegistry {

    private val tools = mutableMapOf<String, BaseTool>()

    fun register(tool: BaseTool) {
        tools[tool.name] = tool
    }

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun getTool(name: String): BaseTool? = tools[name]

    /**
     * 获取所有工具的 OpenAI Function Calling 格式定义
     */
    fun getDefinitions(): List<ToolDefinition> {
        return tools.values.map { it.toToolDefinition() }
    }

    /**
     * 按名称执行工具
     */
    suspend fun execute(name: String, params: Map<String, Any>, context: ToolContext): String {
        val tool = tools[name]
            ?: throw IllegalArgumentException("工具 '$name' 未找到，可用工具: ${tools.keys.joinToString(", ")}")
        return tool.execute(params, context)
    }

    fun getToolNames(): List<String> = tools.keys.toList()
}
