package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val category: String, // "Music", "Video", "Social", "Featured"
    val size: String,
    val progress: Float, // 0.0f to 1.0f
    val status: String, // "DOWNLOADING", "COMPLETED", "FAILED", "PAUSED"
    val timestamp: Long = System.currentTimeMillis(),
    val duration: String = "3:45",
    val isAudioOnly: Boolean = false
)
