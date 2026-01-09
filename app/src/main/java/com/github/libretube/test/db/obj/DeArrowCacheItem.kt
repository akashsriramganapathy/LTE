package com.github.libretube.test.db.obj

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dearrow_cache")
data class DeArrowCacheItem(
    @PrimaryKey val videoId: String,
    val contentJson: String,
    val timestamp: Long = System.currentTimeMillis()
)
