package com.aiagent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 经验实体：存储自主学习得到的任务模板
 */
@Entity(tableName = "experiences")
data class ExperienceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskType: String,        // 任务类型，如"天气查询"、"图片分析"
    val keywords: String,        // 逗号分隔的关键词
    val steps: String,           // JSON 数组格式的步骤描述
    val tools: String,            // JSON 数组格式的工具列表
    val examplePrompt: String,   // Few-shot 模板文本
    val successCount: Int = 0,  // 成功使用次数
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
)
