package com.github.libretube.test.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.libretube.test.db.obj.DeArrowCacheItem

@Dao
interface DeArrowCacheDao {
    @Query("SELECT * FROM dearrow_cache WHERE videoId = :videoId")
    suspend fun get(videoId: String): DeArrowCacheItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DeArrowCacheItem)

    @Query("DELETE FROM dearrow_cache WHERE timestamp < :expiryTime")
    suspend fun deleteOldEntries(expiryTime: Long)

    @Query("DELETE FROM dearrow_cache")
    suspend fun clear()
}
