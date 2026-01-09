package com.github.libretube.test.api

import androidx.core.text.isDigitsOnly
import com.github.libretube.test.api.obj.Playlist
import com.github.libretube.test.api.obj.Playlists
import com.github.libretube.test.api.obj.StreamItem
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.enums.PlaylistType
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.db.DatabaseHolder
import com.github.libretube.test.helpers.ImportHelper
import com.github.libretube.test.repo.LocalPlaylistsRepository
import com.github.libretube.test.repo.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

object PlaylistsHelper {
    const val MAX_CONCURRENT_IMPORT_CALLS = 5

    private val playlistsRepository: PlaylistRepository
        get() = LocalPlaylistsRepository()

    suspend fun getPlaylists(): List<Playlists> = withContext(Dispatchers.IO) {
        val playlists = playlistsRepository.getPlaylists()
        com.github.libretube.test.util.DeArrowUtil.deArrowPlaylists(sortPlaylists(playlists))
    }

    private fun sortPlaylists(playlists: List<Playlists>): List<Playlists> {
        return when (
            PreferenceHelper.getString(PreferenceKeys.PLAYLISTS_ORDER, "creation_date")
        ) {
            "creation_date" -> playlists
            "creation_date_reversed" -> playlists.reversed()
            "alphabetic" -> playlists.sortedBy { it.name?.lowercase() }
            "alphabetic_reversed" -> playlists.sortedBy { it.name?.lowercase() }
                .reversed()
            "custom" -> playlists.sortedBy { it.orderIndex }
            else -> playlists
        }
    }

    suspend fun getPlaylist(playlistId: String): Playlist {
        // load locally stored playlists with the auth api
        return when (getPlaylistType(playlistId)) {
            PlaylistType.PUBLIC -> MediaServiceRepository.instance.getPlaylist(playlistId)
            else -> playlistsRepository.getPlaylist(playlistId)
        }
    }

    suspend fun getAllPlaylistsWithVideos(playlistIds: List<String>? = null): List<Playlist> {
        return withContext(Dispatchers.IO) {
            (playlistIds ?: getPlaylists().map { it.id!! })
                .map { async { getPlaylist(it) } }
                .awaitAll()
        }
    }

    suspend fun createPlaylist(playlistName: String) =
        playlistsRepository.createPlaylist(playlistName)

    suspend fun addToPlaylist(playlistId: String, vararg videos: StreamItem) =
        withContext(Dispatchers.IO) {
            playlistsRepository.addToPlaylist(playlistId, *videos)
        }

    suspend fun renamePlaylist(playlistId: String, newName: String) =
        playlistsRepository.renamePlaylist(playlistId, newName)

    suspend fun changePlaylistDescription(playlistId: String, newDescription: String) =
        playlistsRepository.changePlaylistDescription(playlistId, newDescription)

    suspend fun removeFromPlaylist(playlistId: String, index: Int) =
        playlistsRepository.removeFromPlaylist(playlistId, index)

    suspend fun updatePlaylistVideos(playlistId: String, videos: List<StreamItem>) =
        playlistsRepository.updatePlaylistVideos(playlistId, videos)


    suspend fun clonePlaylist(playlistId: String) = playlistsRepository.clonePlaylist(playlistId)
    suspend fun deletePlaylist(playlistId: String) = playlistsRepository.deletePlaylist(playlistId)

    fun getPlaylistType(playlistId: String): PlaylistType {
        return if (playlistId.isDigitsOnly()) {
            PlaylistType.LOCAL
        } else {
            PlaylistType.PUBLIC
        }
    }

    suspend fun updateLocalPlaylistOrder(playlists: List<String>) =
        playlistsRepository.updateLocalPlaylistOrder(playlists)

    suspend fun updateBookmarkOrder(playlists: List<String>) = withContext(Dispatchers.IO) {
        playlists.forEachIndexed { index, id ->
             DatabaseHolder.Database.playlistBookmarkDao().updateOrder(id, index)
        }
    }

    suspend fun importPlaylists(playlists: List<ImportHelper.LocalImportPlaylist>) =
        playlistsRepository.importPlaylists(playlists)
}

