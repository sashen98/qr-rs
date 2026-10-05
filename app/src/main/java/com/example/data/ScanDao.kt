package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM scanned_items ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanItem>>

    @Query("SELECT * FROM scanned_items WHERE id = :id LIMIT 1")
    suspend fun getScanById(id: Long): ScanItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(item: ScanItem): Long

    @Update
    suspend fun updateScan(item: ScanItem)

    @Delete
    suspend fun deleteScan(item: ScanItem)

    @Query("DELETE FROM scanned_items WHERE id = :id")
    suspend fun deleteScanById(id: Long)

    @Query("DELETE FROM scanned_items")
    suspend fun clearAll()
}
