package com.github.libretube.test.helpers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import com.github.libretube.test.R
import com.github.libretube.test.api.JsonHelper
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.db.DatabaseHolder.Database
import com.github.libretube.test.extensions.TAG
import com.github.libretube.test.extensions.toastFromMainDispatcher
import com.github.libretube.test.obj.BackupFile
import com.github.libretube.test.obj.PreferenceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.annotation.StringRes
import com.github.libretube.test.db.DatabaseHolder
import com.github.libretube.test.extensions.toastFromMainThread
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Backup and restore the preferences
 */
object BackupHelper {
    val forceStringKeys = listOf(
        PreferenceKeys.MAX_IMAGE_CACHE,
        PreferenceKeys.MAX_CONCURRENT_DOWNLOADS,
        PreferenceKeys.DEFAULT_RESOLUTION,
        PreferenceKeys.DEFAULT_RESOLUTION_MOBILE,
        PreferenceKeys.SB_USER_ID,
        "sponsor_category",
        "selfpromo_category",
        "interaction_category",
        "intro_category",
        "outro_category",
        "filler_category",
        "music_offtopic_category",
        "preview_category",
        "hook_category",
        PreferenceKeys.AUTO_BACKUP_MAX_FILES,
        PreferenceKeys.BUFFERING_GOAL,
        PreferenceKeys.CAPTIONS_SIZE,
        PreferenceKeys.CHECKING_FREQUENCY,
        PreferenceKeys.WATCH_HISTORY_SIZE,
        "auto_backup_type"
    )

    // JSON export logic removed in favor of Database-only backups.
    // Legacy JSON restore logic is preserved in restoreAdvancedBackup below.


    /**
     * Backup the current database to the given [uri]
     */
    suspend fun backupDatabase(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val success = DatabaseExportHelper.exportDatabase(context, uri)
        withContext(Dispatchers.Main) {
            if (success) {
                context.toastFromMainThread(R.string.backup_creation_success)
            } else {
                context.toastFromMainThread(R.string.backup_creation_failed)
            }
        }
    }

    /**
     * Restore the database from the given [uri].
     * Note: This replaces the current database file.
     */
    suspend fun restoreDatabase(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        try {
            // close the database before replacing it
            Database.close()
            
            val dbFile = context.getDatabasePath(DatabaseHolder.DATABASE_NAME)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                dbFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            // No success toast here because we'll prompt for restart immediately in UI
        } catch (e: Exception) {
            Log.e(TAG(), "Error while restoring database: $e")
            withContext(Dispatchers.Main) {
                context.toastFromMainThread(R.string.fail)
            }
        }
    }

    /**
     * Restore data from a [BackupFile] or a Database file
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun restoreAdvancedBackup(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        // Check if it is a database file (based on extension or content, but URI doesn't give content easily without reading)
        // Let's try to read it as a DB first if filename ends in .db, otherwise try JSON
        
        // Simple check on URI path/name isn't always reliable with content://, but we can try.
        // If it fails to parse as JSON, we might fallback?
        // Or we decide based on signature.
        
        // Let's try to detect if it's a SQLite file.
        val isSqlite = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(16)
                if (input.read(header) == 16) {
                    val headerString = String(header)
                    headerString.startsWith("SQLite format 3")
                } else false
            } ?: false
        } catch (e: Exception) {
            false
        }

        if (isSqlite) {
            val success = com.github.libretube.test.helpers.DatabaseExportHelper.importDatabase(context, uri)
            if (success) {
                com.github.libretube.test.helpers.RoomPreferenceDataStore.forceReinitialize()
            }
            withContext<Unit>(Dispatchers.Main) {
                if (success) {
                    context.toastFromMainDispatcher(R.string.backup_restore_success)
                } else {
                    context.toastFromMainDispatcher(R.string.backup_restore_failed)
                }
            }
            return@withContext
        }

        // Fallback to Legacy JSON Restore
        val backupFile = try {
            Log.d(TAG(), "Attempting safe restore of backup...")
            context.contentResolver.openInputStream(uri)?.use {
                JsonHelper.json.decodeFromStream(BackupFile.serializer(), it)
            }
        } catch (e: Exception) {
            Log.e(TAG(), "Error while reading backup: $e")
            context.toastFromMainDispatcher(R.string.backup_file_corrupted)
            null
        } ?: return@withContext

        Database.watchHistoryDao().insertAll(backupFile.watchHistory.orEmpty())
        Database.searchHistoryDao().insertAll(backupFile.searchHistory.orEmpty())
        Database.watchPositionDao().insertAll(backupFile.watchPositions.orEmpty())
        Database.localSubscriptionDao().insertAll(backupFile.subscriptions.orEmpty())

        Database.playlistBookmarkDao().insertAll(backupFile.playlistBookmarks.orEmpty())
        Database.subscriptionGroupsDao().insertAll(backupFile.groups.orEmpty())

        backupFile.localPlaylists?.forEach {
            // the playlist will be created with an id of 0, so that Room will auto generate a
            // new playlist id to avoid conflicts with existing local playlists
            val playlistId = Database.localPlaylistsDao().createPlaylist(it.playlist.copy(id = 0))
            it.videos.forEach { playlistItem ->
                playlistItem.playlistId = playlistId.toInt()
                Database.localPlaylistsDao().addPlaylistVideo(playlistItem.copy(id = 0))
            }
        }

        restorePreferences(context, backupFile.preferences)
    }

    /**
     * Restore the shared preferences from a backup file
     */
    private fun restorePreferences(context: Context, preferences: List<PreferenceItem>?) {
        if (preferences == null) return

        PreferenceHelper.clearPreferences()

        // decide for each preference which type it is and save it to the preferences
        val ignoredKeys = listOf(
            PreferenceKeys.GRID_COLUMNS_PORTRAIT,
            PreferenceKeys.GRID_COLUMNS_LANDSCAPE,
            PreferenceKeys.DEFAULT_RESOLUTION,
            PreferenceKeys.DEFAULT_RESOLUTION_MOBILE,
            PreferenceKeys.PLAYER_AUDIO_QUALITY,
            PreferenceKeys.PLAYER_AUDIO_QUALITY_MOBILE,
            PreferenceKeys.NOTIFICATION_ENABLED,
            PreferenceKeys.SHOW_STREAM_THUMBNAILS,
            PreferenceKeys.SHORTS_NOTIFICATIONS,
            PreferenceKeys.CHECKING_FREQUENCY,
            PreferenceKeys.REQUIRED_NETWORK,
            PreferenceKeys.IGNORED_NOTIFICATION_CHANNELS,
            PreferenceKeys.NOTIFICATION_TIME_ENABLED,
            PreferenceKeys.NOTIFICATION_START_TIME,
            PreferenceKeys.NOTIFICATION_END_TIME,
            PreferenceKeys.MAX_CONCURRENT_DOWNLOADS,
            PreferenceKeys.DOUBLE_TAP_TO_SEEK,
            // we want to ensure autoplay playlists is TRUE, so if the backup has it as FALSE, we might want to ignore it 
            // OR we let the XML default take over if we clear it. 
            // But restorePreferences CLEARS all preferences first. 
            // So if we ignore it, it won't be set, and when the app reads it, it will use the default.
            // The default I set in XML is TRUE. So ignoring it is correct if we want to enforce the new default.
            PreferenceKeys.AUTOPLAY_PLAYLISTS, 
        )

        preferences.forEach { (key, jsonValue) ->
            if (key == null || ignoredKeys.contains(key)) return@forEach

            // Force deserialization as String for known string keys to prevent ClassCastException
            if (key in forceStringKeys) {
                PreferenceHelper.putString(key, jsonValue.content)
                return@forEach
            }

            val value = if (jsonValue.isString) {
                jsonValue.content
            } else {
                jsonValue.booleanOrNull
                    ?: jsonValue.intOrNull
                    ?: jsonValue.longOrNull
                    ?: jsonValue.floatOrNull
            }
            when (value) {
                is Boolean -> PreferenceHelper.putBoolean(key, value)
                is Float -> PreferenceHelper.putFloat(key, value)
                is Long -> PreferenceHelper.putLong(key, value)
                is Int -> {
                    // we only use integers for SponsorBlock colors and the start fragment
                    if (key == PreferenceKeys.START_FRAGMENT || "_color" in key) {
                        PreferenceHelper.putInt(key, value)
                    } else {
                        PreferenceHelper.putLong(key, value.toLong())
                    }
                }

                is String -> {
                    if (
                        key == PreferenceKeys.HOME_TAB_CONTENT ||
                        key == PreferenceKeys.SELECTED_FEED_FILTERS
                    ) {
                        PreferenceHelper.putStringSet(key, value.split(",").toSet())
                    } else {
                        PreferenceHelper.putString(key, value)
                    }
                }
            }
        }

        // re-schedule the notification worker as some settings related to it might have changed
        NotificationHelper.enqueueWork(context, ExistingPeriodicWorkPolicy.UPDATE)
    }
}

