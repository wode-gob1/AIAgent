package com.aiagent.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiagent.data.ExperienceEntity
import com.aiagent.experience.ExperienceParserResult
import kotlinx.coroutines.launch

/**
 * 经验管理页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperienceScreen(
    getExperiences: suspend () -> List<ExperienceEntity>,
    onDelete: (Long) -> Unit,
    onParseDemo: suspend (String) -> ExperienceParserResult
) {
    var experiences by remember { mutableStateOf(listOf<ExperienceEntity>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var parseMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        experiences = getExperiences()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("经验库") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加经验")
            }
        }
    ) { padding ->
        if (experiences.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无经验",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Agent 完成任务后会自动学习\n你也可以手动添加示范",
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(experiences, key = { it.id }) { exp ->
                    ExperienceCard(
                        experience = exp,
                        onDelete = {
                            scope.launch {
                                onDelete(exp.id)
                                experiences = getExperiences()
                            }
                        }
                    )
                }
            }
        }
    }

    // 添加经验对话框
    if (showAddDialog) {
        AddExperienceDialog(
            onDismiss = { showAddDialog = false },
            onParse = { demoText ->
                parseMessage = null
                scope.launch {
                    val result = onParseDemo(demoText)
                    when (result) {
                        is ExperienceParserResult.Success -> {
                            showAddDialog = false
                            experiences = getExperiences()
                        }
                        is ExperienceParserResult.Error -> {
                            parseMessage = result.message
                        }
                    }
                }
            },
            parseMessage = parseMessage
        )
    }
}

@Composable
private fun ExperienceCard(
    experience: ExperienceEntity,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = experience.taskType,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "关键词: ${experience.keywords}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row {
                    Badge {
                        Text("${experience.successCount}")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "次使用",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "步骤:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = experience.steps,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "工具:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = experience.tools,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "模板:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = experience.examplePrompt,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 创建时间
                Text(
                    text = "创建于: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(experience.createdAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 删除按钮
                OutlinedButton(
                    onClick = { onDelete() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun AddExperienceDialog(
    onDismiss: () -> Unit,
    onParse: (String) -> Unit,
    parseMessage: String?
) {
    var demoText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加经验示范") },
        text = {
            Column {
                Text(
                    text = "请输入任务示范文本，例如：\n" +
                            "任务: 查询天气\n" +
                            "步骤: 1.使用搜索工具查询 2.整理结果\n" +
                            "工具: web_search",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = demoText,
                    onValueChange = { demoText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    placeholder = { Text("输入示范文本...") }
                )

                parseMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onParse(demoText) },
                enabled = demoText.isNotBlank()
            ) {
                Text("解析并保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
