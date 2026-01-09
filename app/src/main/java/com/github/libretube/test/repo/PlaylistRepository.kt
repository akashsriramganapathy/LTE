package com.github.libretube.test.repo

import com.github.libretube.test.api.obj.Playlist
import com.github.libretube.test.api.obj.Playlists
import com.github.libretube.test.api.obj.StreamItem
import com.github.libretube.test.helpers.ImportHelper

interface PlaylistRepository {
    suspend fun getPlaylist(playlistId: String): Playlist
    suspend fun getPlaylists(): List<Playlists>
    suspend fun addToPlaylist(playlistId: String, vararg videos: StreamItem): Boolean
    suspend fun renamePlaylist(playlistId: String, newName: String): Boolean
    suspend fun changePlaylistDescription(playlistId: String, newDescription: String): Boolean
    suspend fun clonePlaylist(playlistId: String): String?
    suspend fun removeFromPlaylist(playlistId: String, index: Int): Boolean
    suspend fun importPlaylists(playlists: List<ImportHelper.LocalImportPlaylist>)
    suspend fun createPlaylist(playlistName: String): String?
    suspend fun deletePlaylist(playlistId: String): Boolean
    suspend fun updatePlaylistVideos(playlistId: String, videos: List<StreamItem>): Boolean
    suspend fun updateLocalPlaylistOrder(playlists: List<String>)
}
