package com.aiagent.agent

/**
 * Agent 执行状态
 */
sealed class AgentResult {
    data class Success(val content: String, val toolCalls: List<String> = emptyList()) : AgentResult()
    data class Failure(val error: String) : AgentResult()
    data class Thinking(val message: String = "正在思考...") : AgentResult()
}

/**
 * 对话消息模型（UI 层使用）
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user", "assistant", "tool"
    val content: String,
    val imageBase64: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val toolName: String? = null
)
