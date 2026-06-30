package com.aiagent.experience

import com.aiagent.data.ExperienceDao
import com.aiagent.data.ExperienceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 经验匹配器：基于用户输入检索相关经验，生成 Few-shot 提示
 * 
 * 匹配策略：
 * 1. 关键词匹配：用户输入中包含经验的关键词
 * 2. 按成功使用次数排序，取最相关的 N 条
 */
class ExperienceMatcher(private val dao: ExperienceDao) {

    companion object {
        private const val MAX_MATCHES = 3
    }

    /**
     * 根据用户输入匹配相关经验，生成 Few-shot 提示文本
     * @param userInput 用户输入文本
     * @return 匹配到的经验拼接的 Few-shot 提示，无匹配时返回空字符串
     */
    suspend fun matchAsPrompt(userInput: String): String {
        return withContext(Dispatchers.IO) {
            val allExperiences = try {
                dao.getAll()
            } catch (e: Exception) {
                emptyList()
            }

            if (allExperiences.isEmpty()) return@withContext ""

            // 计算每条经验与用户输入的匹配分数
            val scored = allExperiences.map { exp ->
                val score = calculateMatchScore(userInput, exp)
                MatchedExperience(exp, score)
            }

            // 按分数降序排序，取前 N 条
            val matched = scored
                .filter { it.score > 0 }
                .sortedByDescending { it.score }
                .take(MAX_MATCHES)

            if (matched.isEmpty()) return@withContext ""

            // 更新 lastUsedAt 并增加使用次数（异步，不阻塞）
            matched.forEach { m ->
                try {
                    dao.incrementSuccessCount(m.experience.id)
                } catch (_: Exception) { }
            }

            // 生成 Few-shot 提示
            matched.joinToString("\n\n") { m ->
                "--- 相关经验: ${m.experience.taskType} ---\n${m.experience.examplePrompt}"
            }
        }
    }

    /**
     * 计算匹配分数
     */
    private fun calculateMatchScore(input: String, exp: ExperienceEntity): Int {
        var score = 0
        val inputLower = input.lowercase()

        // 关键词匹配
        val keywords = exp.keywords.split(",").map { it.trim().lowercase() }
        val matchedKeywords = keywords.count { kw ->
            kw.isNotEmpty() && inputLower.contains(kw)
        }
        score += matchedKeywords * 10

        // 任务类型匹配
        if (inputLower.contains(exp.taskType.lowercase())) {
            score += 20
        }

        // 成功次数加权（轻微）
        score += minOf(exp.successCount, 5)

        return score
    }

    private data class MatchedExperience(
        val experience: ExperienceEntity,
        val score: Int
    )
}
