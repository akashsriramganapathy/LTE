package com.github.libretube.test.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.github.libretube.test.db.obj.Download
import com.github.libretube.test.db.obj.DownloadChapter
import com.github.libretube.test.db.obj.DownloadItem
import com.github.libretube.test.db.obj.DownloadPlaylist
import com.github.libretube.test.db.obj.DownloadPlaylistVideosCrossRef
import com.github.libretube.test.db.obj.DownloadPlaylistWithDownload
import com.github.libretube.test.db.obj.DownloadPlaylistWithDownloadWithItems
import com.github.libretube.test.db.obj.DownloadWithItems

@Dao
interface DownloadDao {
    @Transaction
    @Query("SELECT * FROM download")
    fun getAllFlow(): kotlinx.coroutines.flow.Flow<List<DownloadWithItems>>

    @Transaction
    @Query("SELECT * FROM download")
    suspend fun getAll(): List<DownloadWithItems>

    @Transaction
    @Query("SELECT * FROM download WHERE videoId = :videoId")
    suspend fun findById(videoId: String): DownloadWithItems?

    @Query("SELECT EXISTS (SELECT * FROM download WHERE videoId = :videoId)")
    suspend fun exists(videoId: String): Boolean

    @Query("SELECT * FROM downloaditem WHERE id = :id")
    suspend fun findDownloadItemById(id: Int): DownloadItem?

    @Query("SELECT * FROM downloaditem WHERE systemId = :systemId")
    suspend fun findBySystemId(systemId: Long): DownloadItem?

    @Query("SELECT * FROM downloaditem WHERE videoId = :videoId LIMIT 1")
    suspend fun findDownloadItemByVideoId(videoId: String): DownloadItem?

    @Query("SELECT * FROM downloaditem WHERE path = :path LIMIT 1")
    suspend fun findDownloadItemByPath(path: String): DownloadItem?

    @Query("DELETE FROM downloaditem WHERE id = :id")
    suspend fun deleteDownloadItemById(id: Int)

    @Query("SELECT COUNT(*) FROM downloaditem WHERE systemId = -1")
    suspend fun countPendingItems(): Int

    @Query("SELECT * FROM downloaditem")
    suspend fun getAllDownloadItems(): List<DownloadItem>

    // Modified to support Queue: Get items that are ready to be queued/downloaded
    @Query("SELECT * FROM downloaditem WHERE downloadSize = -1 OR (SELECT length(COALESCE(path, '')) FROM downloaditem WHERE id = downloaditem.id) < downloadSize")
    suspend fun getDownloadableItems(): List<DownloadItem>
    
    // For Dispatcher: Get queued items ordered by ID (FIFO)
    // For Dispatcher: Get queued items ordered by ID (FIFO)
    @Query("SELECT * FROM downloaditem WHERE systemId = -1 ORDER BY id ASC")
    suspend fun getQueuedItems(): List<DownloadItem>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDownload(download: Download)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDownloadChapter(downloadChapter: DownloadChapter)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadItem(downloadItem: DownloadItem): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateDownloadItem(downloadItem: DownloadItem)

    @Transaction
    @Delete
    suspend fun deleteDownload(download: Download)

    @Transaction
    suspend fun insertDownloadWithItems(download: Download, items: List<DownloadItem>, chapters: List<DownloadChapter>) {
        insertDownload(download)
        chapters.forEach { insertDownloadChapter(it) }
        items.forEach { item ->
            item.id = insertDownloadItem(item).toInt()
        }
    }

    @Transaction
    @Query("SELECT * FROM downloadPlaylist")
    fun getDownloadPlaylistsFlow(): kotlinx.coroutines.flow.Flow<List<DownloadPlaylistWithDownload>>

    @Transaction
    @Query("SELECT * FROM downloadPlaylist")
    suspend fun getDownloadPlaylists(): List<DownloadPlaylistWithDownload>

    @Transaction
    @Query("SELECT * FROM downloadPlaylist WHERE playlistId = :playlistId")
    suspend fun getDownloadPlaylistById(playlistId: String): DownloadPlaylistWithDownload

    @Transaction
    @Query("SELECT * FROM downloadPlaylist WHERE playlistId = :playlistId")
    suspend fun getDownloadPlaylistByIdIncludingItems(playlistId: String): DownloadPlaylistWithDownloadWithItems

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(downloadPlaylist: DownloadPlaylist)

    /**
     * Connect a [DownloadPlaylist] to a [Download] to link the playlist to the video.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistVideoConnection(crossRef: DownloadPlaylistVideosCrossRef)

    @Suppress("DEPRECATION")
    suspend fun deletePlaylistIncludingVideoRefs(playlist: DownloadPlaylist) {
        deletePlaylistCrossRef(playlist.playlistId)
        deletePlaylist(playlist)
    }

    @Delete
    @Deprecated("Call deletePlaylistIncludingVideoRefs instead!")
    suspend fun deletePlaylist(playlist: DownloadPlaylist)

    @Query("DELETE FROM downloadplaylistvideoscrossref WHERE playlistId = :playlistId")
    @Deprecated("Call deletePlaylistIncludingVideoRefs instead!")
    suspend fun deletePlaylistCrossRef(playlistId: String)

    @Query("SELECT * FROM downloadplaylistvideoscrossref WHERE playlistId = :playlistId")
    suspend fun getVideoIdsFromPlaylist(playlistId: String): List<DownloadPlaylistVideosCrossRef>
}

