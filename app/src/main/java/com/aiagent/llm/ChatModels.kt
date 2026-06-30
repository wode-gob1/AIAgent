package com.aiagent.llm

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

// ========== 请求/响应数据模型 ==========

data class Message(
    val role: String, // "system", "user", "assistant", "tool"
    val content: ContentWrapper? = null,
    @SerializedName("tool_calls")
    val toolCalls: List<ToolCall>? = null,
    @SerializedName("tool_call_id")
    val toolCallId: String? = null,
    val name: String? = null
) {
    /** 便捷属性：获取纯文本内容（如果是字符串） */
    fun textContent(): String? = content?.asString()

    /** 便捷属性：获取内容部分列表（如果是多模态） */
    fun contentParts(): List<ContentPart>? = content?.asParts()
}

/**
 * 内容包装器，解决 Gson 对 Any? 多态类型无法正确反序列化的问题。
 * LLM API 的 content 可以是 string 或 array，此类统一封装。
 */
data class ContentWrapper(
    val rawValue: Any?
) {
    fun asString(): String? = rawValue as? String
    fun asParts(): List<ContentPart>? {
        return when (rawValue) {
            is List<*> -> {
                rawValue.filterIsInstance<ContentPart>().ifEmpty {
                    // Gson 反序列化时可能变成 LinkedHashMap 列表，需要手动转换
                    try {
                        val gson = Gson()
                        val json = gson.toJson(rawValue)
                        gson.fromJson(json, object : TypeToken<List<ContentPart>>() {}.type)
                    } catch (_: Exception) {
                        rawValue.mapNotNull { map ->
                            if (map is Map<*, *>) {
                                val type = map["type"] as? String ?: return@mapNotNull null
                                val text = map["text"] as? String
                                val imageUrlMap = map["image_url"] as? Map<*, *>
                                val imageUrl = imageUrlMap?.let {
                                    ImageUrl(it["url"] as? String ?: "")
                                }
                                ContentPart(type = type, text = text, imageUrl = imageUrl)
                            } else null
                        }
                    }
                }
            }
            is String -> listOf(ContentPart(type = "text", text = rawValue))
            else -> null
        }
    }
}

/**
 * 自定义 Gson 序列化/反序列化器，处理 content 字段的多态类型
 */
class ContentWrapperSerializer : JsonSerializer<ContentWrapper>, JsonDeserializer<ContentWrapper> {
    private val gson = Gson()

    override fun serialize(src: ContentWrapper, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return when (val v = src.rawValue) {
            null -> JsonNull.INSTANCE
            is String -> gson.toJsonTree(v)
            is List<*> -> gson.toJsonTree(v)
            is JsonElement -> v
            else -> gson.toJsonTree(v)
        }
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ContentWrapper {
        return if (json.isJsonNull) {
            ContentWrapper(null)
        } else if (json.isJsonPrimitive && json.asJsonPrimitive.isString) {
            ContentWrapper(json.asString)
        } else if (json.isJsonArray) {
            // 尝试直接反序列化为 List<ContentPart>
            try {
                val type = object : TypeToken<List<ContentPart>>() {}.type
                ContentWrapper(gson.fromJson(json, type))
            } catch (_: Exception) {
                // 降级为原始列表
                ContentWrapper(gson.fromJson(json, List::class.java))
            }
        } else {
            ContentWrapper(json.toString())
        }
    }
}

data class ContentPart(
    val type: String, // "text" or "image_url"
    val text: String? = null,
    @SerializedName("image_url")
    val imageUrl: ImageUrl? = null
)

data class ImageUrl(val url: String) // data:image/jpeg;base64,... 或 http URL

data class ToolCall(
    val id: String? = null,
    val type: String? = "function",
    val function: FunctionCall? = null
)

data class FunctionCall(
    val name: String,
    val arguments: String? = null
)

data class ChatRequest(
    val model: String,
    val messages: List<Any>, // Message 或 Map（手动构建时）
    val tools: List<ToolDefinition>? = null,
    @SerializedName("tool_choice")
    val toolChoice: String? = null,
    val stream: Boolean = false
)

data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDefinition
)

data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>
)

data class ChatResponse(
    val choices: List<Choice>? = null,
    val error: ErrorResponse? = null
)

data class Choice(
    val index: Int? = null,
    val message: Message? = null,
    @SerializedName("finish_reason")
    val finishReason: String? = null
)

data class ErrorResponse(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
