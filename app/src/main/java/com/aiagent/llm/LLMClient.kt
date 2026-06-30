package com.aiagent.llm

import android.util.Log
import com.aiagent.BuildConfig
import com.aiagent.config.ConfigManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException
import java.util.concurrent.TimeUnit

class LLMClient(private val configManager: ConfigManager) {

    companion object {
        private const val TAG = "LLMClient"
    }

    // 缓存的 Gson 实例
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ContentWrapper::class.java, ContentWrapperSerializer())
        .setLenient()
        .create()

    // 缓存的 Retrofit 实例，避免每次请求重建
    @Volatile
    private var cachedRetrofit: Retrofit? = null
    @Volatile
    private var cachedConfigHash: Int? = null

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        // 在 Debug 模式下记录，Release 模式下只记录简短信息
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }.apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.HEADERS // Debug 只记录 headers，避免泄露完整 body
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val authInterceptor = Interceptor { chain ->
        val config = configManager.getConfig()
        val request = chain.request().newBuilder()
        if (config != null) {
            request.header("Authorization", "Bearer ${config.apiKey}")
            request.header("Content-Type", "application/json")
        }
        chain.proceed(request.build())
    }

    // 缓存的 OkHttpClient 实例
    @Volatile
    private var cachedOkHttpClient: OkHttpClient? = null

    /**
     * 获取或创建共享的 OkHttpClient（单例）
     */
    private fun getSharedOkHttpClient(): OkHttpClient {
        return cachedOkHttpClient ?: synchronized(this) {
            cachedOkHttpClient ?: OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)  // LLM 响应可能较慢
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                .addInterceptor(loggingInterceptor)
                .build()
                .also { cachedOkHttpClient = it }
        }
    }

    /**
     * 根据当前配置获取或创建 Retrofit 实例
     */
    private fun getRetrofit(): Retrofit {
        val config = configManager.getConfig()
            ?: throw LLMException("API 未配置，请先在设置中配置 Base URL 和 API Key")

        val newHash = (config.baseUrl + config.apiKey).hashCode()
        if (cachedRetrofit != null && cachedConfigHash == newHash) {
            return cachedRetrofit!!
        }

        val baseUrl = config.baseUrl.trimEnd('/').let {
            if (!it.endsWith("/v1")) "$it/v1" else it
        } + "/"

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(getSharedOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        cachedRetrofit = retrofit
        cachedConfigHash = newHash
        return retrofit
    }

    private fun getApi(): LLMApi {
        return getRetrofit().create(LLMApi::class.java)
    }

    /**
     * 当配置变更时清除缓存
     */
    fun invalidateCache() {
        cachedRetrofit = null
        cachedConfigHash = null
        cachedOkHttpClient = null
    }

    suspend fun chat(request: ChatRequest): ChatResponse {
        return try {
            val response = getApi().chat(request)

            // 检查 API 级别的错误
            response.error?.let {
                throw LLMException("LLM 错误: ${it.message ?: it.type ?: it.code ?: "未知错误"}")
            }

            response
        } catch (e: retrofit2.HttpException) {
            val body = e.response()?.errorBody()?.string()
            Log.e(TAG, "HTTP ${e.code()}: $body")
            throw LLMException("HTTP ${e.code()}: ${body ?: e.message()}")
        } catch (e: IOException) {
            Log.e(TAG, "网络错误: ${e.message}")
            throw LLMException("网络连接失败: ${e.message}")
        } catch (e: LLMException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "未知错误", e)
            throw LLMException("请求失败: ${e.message}")
        }
    }

    class LLMException(message: String) : Exception(message)

    interface LLMApi {
        @POST("chat/completions")
        suspend fun chat(@Body request: ChatRequest): ChatResponse
    }
}
