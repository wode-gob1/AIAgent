package com.aiagent.tools

import com.aiagent.config.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 网络搜索工具：使用 DuckDuckGo Instant Answer API 进行搜索
 * 注意：DuckDuckGo API 无需 Key，但返回的是即时答案摘要，非完整搜索结果。
 * 如需更完整的搜索，可替换为 SerpAPI、Bing Web Search API 等。
 */
class SearchTool : BaseTool() {

    override val name = "web_search"
    override val description = "搜索互联网上的信息。输入搜索关键词，返回相关结果摘要。"

    override val parametersSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to "搜索关键词"
            )
        ),
        "required" to listOf("query")
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(params: Map<String, Any>, context: ToolContext): String {
        val query = params["query"] as? String ?: return "错误：缺少搜索关键词"

        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AIAgent/1.0")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext "搜索失败：无响应内容"
                val json = JSONObject(body)

                val results = mutableListOf<String>()

                // Abstract（即时答案摘要）
                val abstract = json.optString("Abstract", "")
                if (abstract.isNotEmpty()) {
                    results.add("摘要: $abstract")
                    val source = json.optString("AbstractSource", "")
                    if (source.isNotEmpty()) {
                        results.add("来源: $source")
                    }
                }

                // RelatedTopics（相关主题）
                val relatedTopics = json.optJSONArray("RelatedTopics")
                if (relatedTopics != null) {
                    val count = minOf(relatedTopics.length(), 5)
                    for (i in 0 until count) {
                        val topic = relatedTopics.optJSONObject(i)
                        if (topic != null) {
                            val text = topic.optString("Text", "")
                            if (text.isNotEmpty()) {
                                results.add("- $text")
                            }
                        }
                    }
                }

                // Heading
                val heading = json.optString("Heading", "")
                if (heading.isNotEmpty() && results.isEmpty()) {
                    results.add("主题: $heading")
                }

                if (results.isEmpty()) {
                    "未找到与 '$query' 相关的搜索结果。可以尝试更换关键词。"
                } else {
                    results.joinToString("\n")
                }
            } catch (e: Exception) {
                "搜索出错: ${e.message}"
            }
        }
    }
}
