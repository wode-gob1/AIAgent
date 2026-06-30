package com.aiagent.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.util.concurrent.TimeUnit

class ConfigManager(context: Context) {

    companion object {
        private const val TAG = "ConfigManager"
        private const val PREFS_NAME = "secure_config"
        private const val PREFS_FALLBACK_NAME = "config_fallback"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val DEFAULT_MODEL = "gpt-4o"
    }

    data class ApiConfig(
        val baseUrl: String,
        val apiKey: String,
        val model: String = DEFAULT_MODEL
    )

    private val appContext = context.applicationContext
    private var prefs: SharedPreferences
    private var isEncrypted = true

    init {
        prefs = try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "加密存储初始化失败，降级为普通 SharedPreferences", e)
            isEncrypted = false
            appContext.getSharedPreferences(PREFS_FALLBACK_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveConfig(config: ApiConfig) {
        try {
            prefs.edit().apply {
                putString(KEY_BASE_URL, config.baseUrl)
                putString(KEY_API_KEY, config.apiKey)
                putString(KEY_MODEL, config.model)
            }.apply()
        } catch (e: Exception) {
            Log.e(TAG, "保存配置失败", e)
        }
    }

    fun getConfig(): ApiConfig? {
        return try {
            val url = prefs.getString(KEY_BASE_URL, null) ?: return null
            val key = prefs.getString(KEY_API_KEY, null) ?: return null
            val model = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
            ApiConfig(url, key, model)
        } catch (e: Exception) {
            Log.e(TAG, "读取配置失败", e)
            null
        }
    }

    fun hasConfig(): Boolean {
        return try {
            prefs.contains(KEY_BASE_URL) && prefs.contains(KEY_API_KEY)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 验证 URL 格式是否合法
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val parsed = URL(url)
            parsed.protocol == "http" || parsed.protocol == "https"
        } catch (e: Exception) {
            false
        }
    }

    suspend fun testConnection(config: ApiConfig): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = config.baseUrl.trimEnd('/')
                if (baseUrl.isBlank()) {
                    Log.w(TAG, "Base URL 为空")
                    return@withContext false
                }

                // URL 格式校验
                if (!isValidUrl(baseUrl)) {
                    Log.w(TAG, "Base URL 格式不合法: $baseUrl")
                    return@withContext false
                }

                val finalUrl = if (baseUrl.endsWith("/v1")) baseUrl else "$baseUrl/v1"
                val testUrl = "$finalUrl/models"

                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(testUrl)
                    .header("Authorization", "Bearer ${config.apiKey}")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                response.use { it.isSuccessful }
            } catch (e: Exception) {
                Log.w(TAG, "测试连接失败: ${e.message}")
                false
            }
        }
    }
}
