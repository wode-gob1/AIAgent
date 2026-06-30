package com.aiagent

import android.app.Application
import com.aiagent.config.ConfigManager
import com.aiagent.data.AppDatabase
import com.aiagent.llm.LLMClient

class AIAgentApplication : Application() {
    val configManager by lazy { ConfigManager(this) }
    val database by lazy { AppDatabase.getInstance(this) }
    val llmClient by lazy { LLMClient(configManager) }
}
