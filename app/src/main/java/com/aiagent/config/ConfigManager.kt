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
import java.util.concurrent.TimeUnit

class ConfigManager(context: Context) {

    companion object {
        private const val TAG = "ConfigManager"
        private const val PREFS_NAME = "secure_config"
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

    private val masterKey: MasterKey
    private val prefs: SharedPreferences

    init {
        masterKey = try {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "创建 MasterKey 失败，使用降级存储", e)
            throw e
        }
        prefs = try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "创建 EncryptedSharedPreferences 失败", e)
            throw e
        }
    }

    fun saveConfig(config: ApiConfig) {
        prefs.edit().apply {
            putString(KEY_BASE_URL, config.baseUrl)
            putString(KEY_API_KEY, config.apiKey)
            putString(KEY_MODEL, config.model)
        }.apply()
    }

    fun getConfig(): ApiConfig? {
        val url = prefs.getString(KEY_BASE_URL, null) ?: return null
        val key = prefs.getString(KEY_API_KEY, null) ?: return null
        val model = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        return ApiConfig(url, key, model)
    }

    fun hasConfig(): Boolean {
        return prefs.contains(KEY_BASE_URL) && prefs.contains(KEY_API_KEY)
    }

    suspend fun testConnection(config: ApiConfig): Boolean {
        val baseUrl = config.baseUrl.trimEnd('/')
        val finalUrl = if (baseUrl.endsWith("/v1")) baseUrl else "$baseUrl/v1"
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("$finalUrl/models")
            .header("Authorization", "Bearer ${config.apiKey}")
            .get() // 使用 GET 替代 HEAD，兼容性更好
            .build()
        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                response.use { it.isSuccessful }
            } catch (e: Exception) {
                Log.w(TAG, "测试连接失败: ${e.message}")
                false
            }
        }
    }
}
