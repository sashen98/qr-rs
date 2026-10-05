package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_items")
data class ScanItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawValue: String,
    val format: String,
    val title: String,
    val price: String?,
    val priceValue: Double?,
    val currency: String = "Rs.",
    val category: String = "General",
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
