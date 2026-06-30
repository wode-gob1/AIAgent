package com.aiagent.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * 图片分析工具：将图片通过 base64 编码传给多模态 LLM 进行分析
 */
class ImageAnalysisTool : BaseTool() {

    override val name = "analyze_image"
    override val description = "分析图片内容，描述图片中的物体、场景、文字等信息。传入 base64 编码的图片数据和分析指令。"

    override val parametersSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "image_base64" to mapOf(
                "type" to "string",
                "description" to "图片的 base64 编码字符串（含 data:image/jpeg;base64, 前缀）"
            ),
            "instruction" to mapOf(
                "type" to "string",
                "description" to "分析指令，例如'描述这张图片的内容'"
            )
        ),
        "required" to listOf("image_base64", "instruction")
    )

    override suspend fun execute(params: Map<String, Any>, context: ToolContext): String {
        val imageBase64 = params["image_base64"] as? String ?: return "错误：缺少图片数据"
        val instruction = params["instruction"] as? String ?: "请描述这张图片的内容"

        val config = context.configManager.getConfig()
            ?: return "错误：API 未配置"

        return try {
            val messages = listOf(
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf("type" to "text", "text" to instruction),
                        mapOf("type" to "image_url", "image_url" to mapOf("url" to imageBase64))
                    )
                )
            )
            val request = com.aiagent.llm.ChatRequest(
                model = config.model,
                messages = messages,
                tools = null,
                toolChoice = null
            )
            val response = context.llmClient.chat(request)
            response.choices?.firstOrNull()?.message?.textContent()
                ?: "分析完成但未获得结果"
        } catch (e: Exception) {
            "图片分析失败: ${e.message}"
        }
    }
}

/**
 * Bitmap 转 Base64 字符串工具函数
 * 安全处理：只在原始和缩放尺寸不同时创建新的 scaledBitmap
 */
fun bitmapToBase64(bitmap: Bitmap, quality: Int = 60): String {
    val maxSize = 1024
    val width = bitmap.width
    val height = bitmap.height

    val needsResize = width > maxSize || height > maxSize

    val bitmapToCompress = if (needsResize) {
        val scale = if (width > height) maxSize.toFloat() / width else maxSize.toFloat() / height
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()
        Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    } else {
        bitmap
    }

    return try {
        val outputStream = ByteArrayOutputStream()
        bitmapToCompress.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val bytes = outputStream.toByteArray()
        "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    } finally {
        // 只回收我们创建的新 bitmap，不回收传入的原始 bitmap
        if (needsResize && bitmapToCompress !== bitmap) {
            bitmapToCompress.recycle()
        }
    }
}
