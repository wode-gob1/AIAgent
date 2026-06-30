package com.aiagent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aiagent.config.ConfigManager

/**
 * 设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    configManager: ConfigManager,
    onConfigSaved: () -> Unit
) {
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("gpt-4o") }
    var showApiKey by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        configManager.getConfig()?.let {
            baseUrl = it.baseUrl
            apiKey = it.apiKey
            model = it.model
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API 配置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = "配置你的 LLM API 参数。支持任何 OpenAI 兼容的 API（如 OpenAI、Claude、本地 Ollama 等）。API Key 使用加密存储，不会上传。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text("https://api.openai.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                placeholder = { Text("sk-...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showApiKey) "隐藏密钥" else "显示密钥"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                placeholder = { Text("gpt-4o") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    if (baseUrl.isBlank() || apiKey.isBlank()) {
                        testResult = "请先填写 Base URL 和 API Key"
                        testSuccess = false
                        return@OutlinedButton
                    }
                    isTesting = true
                    testResult = null
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTesting && baseUrl.isNotBlank() && apiKey.isNotBlank()
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("测试中...")
                } else {
                    Text("测试连接")
                }
            }

            testResult?.let { result ->
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (testSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val config = ConfigManager.ApiConfig(
                        baseUrl = baseUrl.trimEnd('/'),
                        apiKey = apiKey,
                        model = model.ifBlank { "gpt-4o" }
                    )
                    configManager.saveConfig(config)
                    testResult = null
                    onConfigSaved()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
            ) {
                Text("保存配置")
            }
        }
    }

    // 测试连接逻辑
    if (isTesting) {
        LaunchedEffect(Unit) {
            val config = ConfigManager.ApiConfig(
                baseUrl = baseUrl.trimEnd('/'),
                apiKey = apiKey,
                model = model.ifBlank { "gpt-4o" }
            )
            val success = configManager.testConnection(config)
            isTesting = false
            testSuccess = success
            testResult = if (success) "连接成功!" else "连接失败，请检查 URL 和 API Key"
        }
    }
}
