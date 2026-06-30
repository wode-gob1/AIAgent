package com.aiagent.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 经验数据访问对象
 */
@Dao
interface ExperienceDao {

    @Query("SELECT * FROM experiences ORDER BY successCount DESC")
    suspend fun getAll(): List<ExperienceEntity>

    @Query("SELECT * FROM experiences ORDER BY successCount DESC")
    fun getAllFlow(): Flow<List<ExperienceEntity>>

    @Query("SELECT * FROM experiences WHERE id = :id")
    suspend fun getById(id: Long): ExperienceEntity?

    @Query("SELECT * FROM experiences WHERE taskType LIKE '%' || :taskType || '%'")
    suspend fun searchByType(taskType: String): List<ExperienceEntity>

    @Query("SELECT * FROM experiences WHERE keywords LIKE '%' || :keyword || '%'")
    suspend fun searchByKeyword(keyword: String): List<ExperienceEntity>

    @Query("SELECT * FROM experiences ORDER BY lastUsedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 10): List<ExperienceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exp: ExperienceEntity): Long

    @Update
    suspend fun update(exp: ExperienceEntity)

    @Delete
    suspend fun delete(exp: ExperienceEntity)

    @Query("DELETE FROM experiences WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE experiences SET successCount = successCount + 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun incrementSuccessCount(id: Long, timestamp: Long = System.currentTimeMillis())
}
