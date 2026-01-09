package com.github.libretube.test.db

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.libretube.test.LibreTubeApp

object DatabaseHolder {
    const val DATABASE_NAME = "LibreTubeDatabase"

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE 'localPlaylist' ADD COLUMN 'description' TEXT DEFAULT NULL")
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE 'playlistBookmark' ADD COLUMN 'videos' INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE 'subscriptionGroups' ADD COLUMN 'index' INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE 'downloadItem' ADD COLUMN 'language' TEXT DEFAULT NULL")
        }
    }

    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE 'watchHistoryItem' ADD COLUMN 'isShort' INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE 'downloadChapters' (" +
                    "id INTEGER PRIMARY KEY NOT NULL, " +
                    "videoId TEXT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "start INTEGER NOT NULL, " +
                    "thumbnailUrl TEXT NOT NULL" +
                    ")")
        }
    }

    private val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `dearrowCache` (`videoId` TEXT NOT NULL, `title` TEXT, `thumbnail` TEXT, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`videoId`))")
        }
    }
    
    private val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Empty migration
        }
    }

    private val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `dearrowCache`")
        }
    }

    private val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `dearrow_cache` (`videoId` TEXT NOT NULL, `contentJson` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`videoId`))")
        }
    }

    private val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE 'downloadItem' ADD COLUMN 'systemId' INTEGER NOT NULL DEFAULT -1")
        }
    }

    private val MIGRATION_28_29 = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
             db.execSQL("ALTER TABLE 'playlistBookmark' ADD COLUMN 'savedAt' INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_29_30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
             db.execSQL("ALTER TABLE 'localPlaylist' ADD COLUMN 'orderIndex' INTEGER NOT NULL DEFAULT 0")
             db.execSQL("ALTER TABLE 'playlistBookmark' ADD COLUMN 'orderIndex' INTEGER NOT NULL DEFAULT 0")
        }
    }

    private var _database: AppDatabase? = null

    val Database: AppDatabase
        get() {
            synchronized(this) {
                if (_database == null) {
                    _database = Room.databaseBuilder(LibreTubeApp.instance, AppDatabase::class.java, DATABASE_NAME)
                        .addMigrations(
                            MIGRATION_11_12,
                            MIGRATION_12_13,
                            MIGRATION_13_14,
                            MIGRATION_14_15,
                            MIGRATION_15_16,
                            MIGRATION_17_18,
                            MIGRATION_23_24,
                            MIGRATION_24_25,
                            MIGRATION_25_26,
                            MIGRATION_26_27,
                            MIGRATION_27_28,
                            MIGRATION_28_29,
                            MIGRATION_29_30
                        )
                        .fallbackToDestructiveMigration()
                        .build()
                }
                return _database!!
            }
        }

    fun close() {
        synchronized(this) {
            if (_database?.isOpen == true) {
                _database?.close()
            }
            _database = null
        }
    }
}

