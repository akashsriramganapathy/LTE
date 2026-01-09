package com.github.libretube.test.helpers

import android.content.Context
import android.net.Uri
import com.github.libretube.test.db.DatabaseHolder
import com.github.libretube.test.extensions.TAG
import com.github.libretube.logger.FileLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DatabaseExportHelper {
    private const val DB_NAME = "LibreTubeDatabase"

    fun exportDatabase(context: Context, uri: Uri): Boolean {
        return try {
            // 1. Force Vacuum to compact DB and merge WAL
            val db = DatabaseHolder.Database.openHelper.writableDatabase
            db.query("VACUUM").close()
            
            // 2. Checkpoint to be doubly sure detailed data is flushed
            db.query("PRAGMA wal_checkpoint(FULL)").close()

            // 3. Close the global database connection to release file locks
            DatabaseHolder.close()

            val dbPath = context.getDatabasePath(DB_NAME)
            if (!dbPath.exists()) {
                FileLogger.e(TAG(), "Database file not found: ${dbPath.absolutePath}")
                return false
            }

            context.contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(dbPath).use { input ->
                    input.copyTo(output)
                }
            }
            FileLogger.i(TAG(), "Database exported successfully to $uri")
            true
        } catch (e: Exception) {
            FileLogger.e(TAG(), "Failed to export database", e)
            false
        }
    }

    fun importDatabase(context: Context, uri: Uri): Boolean {
        return try {
            val dbPath = context.getDatabasePath(DB_NAME)
            
            // Close the database to ensure we can write to it safely
            DatabaseHolder.close()

            // Copy the new database file
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dbPath).use { output ->
                    input.copyTo(output)
                }
            }

            // Delete WAL and SHM files to prevent inconsistency
            val walFile = File(dbPath.parent, "$DB_NAME-wal")
            val shmFile = File(dbPath.parent, "$DB_NAME-shm")
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            FileLogger.i(TAG(), "Database imported successfully from $uri")
            
            // Note: The app should ideally restart or re-initialize the DB connection after this.
            // DatabaseHolder.Database will re-open on next access.
            true
        } catch (e: Exception) {
            FileLogger.e(TAG(), "Failed to import database", e)
            false
        }
    }
}

