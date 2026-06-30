package com.aiagent.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aiagent.agent.AgentLoop
import com.aiagent.agent.AgentResult
import com.aiagent.agent.ChatMessage
import com.aiagent.tools.bitmapToBase64
import kotlinx.coroutines.launch
import java.io.InputStream

/**
 * 聊天页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    agentLoop: AgentLoop
) {
    var inputText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val imageUri = remember { mutableStateOf<Uri?>(null) }
    val selectedBitmap = remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // 收集 Agent 状态
    val agentState by agentLoop.state.collectAsStateWithLifecycle()

    // 响应 Agent 状态变化
    LaunchedEffect(agentState) {
        when (val state = agentState) {
            is AgentResult.Thinking -> {
                statusText = state.message
            }
            is AgentResult.Success -> {
                messages.add(
                    ChatMessage(role = "assistant", content = state.content)
                )
                isProcessing = false
                statusText = ""
                imageUri.value = null
                selectedBitmap.value = null
                listState.animateScrollToItem(messages.size)
            }
            is AgentResult.Failure -> {
                messages.add(
                    ChatMessage(role = "assistant", content = "抱歉: ${state.error}")
                )
                isProcessing = false
                statusText = ""
            }
            null -> {}
        }
    }

    // 图片选择器
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            imageUri.value = uri
            // 将 Uri 转为 Bitmap
            coroutineScope.launch {
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    selectedBitmap.value = bitmap
                } catch (e: Exception) {
                    imageUri.value = null
                    selectedBitmap.value = null
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Agent") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = {
                        agentLoop.resetConversation()
                        messages.clear()
                        statusText = ""
                        imageUri.value = null
                        selectedBitmap.value = null
                    }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清除对话")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                // 图片预览
                imageUri.value?.let { uri ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "选择的图片",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                        IconButton(
                            onClick = {
                                imageUri.value = null
                                selectedBitmap.value = null
                            },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "移除图片",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // 状态提示
                if (statusText.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // 输入区域
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // 附件按钮
                        IconButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = "添加图片")
                        }

                        // 输入框
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("输入消息...") },
                            enabled = !isProcessing,
                            maxLines = 4
                        )

                        // 发送按钮
                        IconButton(
                            onClick = {
                                val text = inputText.trim()
                                if (text.isEmpty()) return@IconButton

                                isProcessing = true
                                val bitmap = selectedBitmap.value
                                // 获取图片的 base64 用于消息气泡显示（使用安全缩放）
                                val imgBase64 = bitmap?.let { bitmapToBase64(it) }

                                messages.add(
                                    ChatMessage(
                                        role = "user",
                                        content = text,
                                        imageBase64 = imgBase64
                                    )
                                )
                                inputText = ""

                                coroutineScope.launch {
                                    try {
                                        listState.animateScrollToItem(messages.size)
                                        agentLoop.run(text, image = bitmap)
                                    } catch (e: Exception) {
                                        messages.add(
                                            ChatMessage(
                                                role = "assistant",
                                                content = "请求出错: ${e.message ?: "未知错误"}"
                                            )
                                        )
                                    } finally {
                                        isProcessing = false
                                        statusText = ""
                                    }
                                }
                            },
                            enabled = !isProcessing && inputText.isNotBlank()
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "发送",
                                tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "你好！我是你的 AI 助手",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "可以发送文字或图片与我对话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(12.dp)
        ) {
            message.imageBase64?.let { base64 ->
                AsyncImage(
                    model = base64,
                    contentDescription = "用户图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}
