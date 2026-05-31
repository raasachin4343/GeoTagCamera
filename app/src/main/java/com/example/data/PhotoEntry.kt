package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_entries")
data class PhotoEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val address: String,
    val timestamp: Long
)
