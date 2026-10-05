package com.example.data

import kotlinx.coroutines.flow.Flow

class ScanRepository(private val scanDao: ScanDao) {
    val allScans: Flow<List<ScanItem>> = scanDao.getAllScans()

    suspend fun insert(item: ScanItem): Long {
        return scanDao.insertScan(item)
    }

    suspend fun update(item: ScanItem) {
        scanDao.updateScan(item)
    }

    suspend fun delete(item: ScanItem) {
        scanDao.deleteScan(item)
    }

    suspend fun deleteById(id: Long) {
        scanDao.deleteScanById(id)
    }

    suspend fun clearAll() {
        scanDao.clearAll()
    }
}
