package com.appblocker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.appblocker.data.local.entity.MotivationalMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MotivationalMessageDao {

    @Insert
    suspend fun insert(message: MotivationalMessageEntity)

    @Delete
    suspend fun delete(message: MotivationalMessageEntity)

    @Update
    suspend fun update(message: MotivationalMessageEntity)

    @Query("SELECT * FROM motivational_messages WHERE app_package_name = :packageName")
    fun getMessagesForApp(packageName: String): Flow<List<MotivationalMessageEntity>>

    @Query("SELECT * FROM motivational_messages WHERE app_package_name IS NULL")
    fun getGeneralMessages(): Flow<List<MotivationalMessageEntity>>

    @Query(
        """SELECT * FROM motivational_messages
        WHERE app_package_name = :packageName OR app_package_name IS NULL
        ORDER BY RANDOM() LIMIT 1"""
    )
    suspend fun getRandomMessageForApp(packageName: String): MotivationalMessageEntity?
}
