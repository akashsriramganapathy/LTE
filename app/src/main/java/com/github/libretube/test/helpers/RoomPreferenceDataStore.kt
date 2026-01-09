package com.github.libretube.test.helpers

import androidx.preference.PreferenceDataStore
import com.github.libretube.test.db.DatabaseHolder
import com.github.libretube.test.db.obj.AppSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

object RoomPreferenceDataStore : PreferenceDataStore() {
    private val cache = ConcurrentHashMap<String, String>()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isInitialized = false
    private var initJob: kotlinx.coroutines.Job? = null

    private val _preferenceFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val preferenceFlow = _preferenceFlow.asSharedFlow()

    fun initializeAsync() {
        if (isInitialized || initJob?.isActive == true) return
        
        initJob = scope.launch {
            loadCache()
        }
    }

    fun getStringFlow(key: String, defValue: String?): Flow<String?> = preferenceFlow
        .filter { it == key || it == "*" }
        .onStart { emit(key) }
        .map { getString(key, defValue) }

    fun getBooleanFlow(key: String, defValue: Boolean): Flow<Boolean> = preferenceFlow
        .filter { it == key || it == "*" }
        .onStart { emit(key) }
        .map { getBoolean(key, defValue) }

    fun getIntFlow(key: String, defValue: Int): Flow<Int> = preferenceFlow
        .filter { it == key || it == "*" }
        .onStart { emit(key) }
        .map { getInt(key, defValue) }

    /**
     * Synchronously load the cache from the database.
     * Should be called from Application.onCreate() to avoid later blocking.
     */
    fun initializeBlocking() {
        if (isInitialized) return
        runBlocking(Dispatchers.IO) {
            loadCache()
        }
    }

    fun forceReinitialize() {
        isInitialized = false
        initJob?.cancel()
        initializeAsync()
    }

    private suspend fun loadCache() {
        try {
            val settings = DatabaseHolder.Database.appSettingsDao().getAll()
            cache.clear()
            settings.forEach { cache[it.key] = it.value }
            isInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Helper to wait for initialization (as a fallback)
    private fun ensureInitialized() {
        if (isInitialized) return
        
        // If we reach here, it means initializeBlocking wasn't called or failed.
        // We still need the data, so we'll have to block, but this should be rare.
        runBlocking(Dispatchers.IO) {
            loadCache()
        }
    }

    fun getAll(): Map<String, String> {
        ensureInitialized()
        return cache.toMap()
    }

    override fun putString(key: String, value: String?) {
        updateCacheAndDb(key, value)
    }

    override fun putStringSet(key: String, values: Set<String>?) {
        updateCacheAndDb(key, values?.joinToString(","))
    }

    override fun putInt(key: String, value: Int) {
        updateCacheAndDb(key, value.toString())
    }

    override fun putLong(key: String, value: Long) {
        updateCacheAndDb(key, value.toString())
    }

    override fun putFloat(key: String, value: Float) {
        updateCacheAndDb(key, value.toString())
    }

    override fun putBoolean(key: String, value: Boolean) {
        updateCacheAndDb(key, value.toString())
    }

    override fun getString(key: String, defValue: String?): String? {
        ensureInitialized()
        return cache[key] ?: defValue
    }

    override fun getStringSet(key: String, defValue: Set<String>?): Set<String>? {
        ensureInitialized()
        return cache[key]?.split(",")?.toSet() ?: defValue
    }

    override fun getInt(key: String, defValue: Int): Int {
        ensureInitialized()
        return cache[key]?.toIntOrNull() ?: defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        ensureInitialized()
        return cache[key]?.toLongOrNull() ?: defValue
    }

    override fun getFloat(key: String, defValue: Float): Float {
        ensureInitialized()
        return cache[key]?.toFloatOrNull() ?: defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        ensureInitialized()
        return cache[key]?.toBoolean() ?: defValue
    }

    private fun updateCacheAndDb(key: String, value: String?) {
        if (value == null) {
            cache.remove(key)
            scope.launch {
                try {
                    DatabaseHolder.Database.appSettingsDao().delete(key)
                } catch (e: Exception) {
                    com.github.libretube.logger.FileLogger.e("RoomDataStore", "Failed to delete preference: $key", e)
                }
            }
        } else {
            cache[key] = value
            scope.launch {
                try {
                    DatabaseHolder.Database.appSettingsDao().insert(AppSetting(key, value))
                } catch (e: Exception) {
                    com.github.libretube.logger.FileLogger.e("RoomDataStore", "Failed to save preference: $key", e)
                }
            }
        }
        _preferenceFlow.tryEmit(key)
    }
    
    fun clear() {
        cache.clear()
        scope.launch {
            try {
                DatabaseHolder.Database.appSettingsDao().deleteAll()
            } catch (e: Exception) {
                com.github.libretube.logger.FileLogger.e("RoomDataStore", "Failed to clear preferences", e)
            }
        }
        _preferenceFlow.tryEmit("*")
    }
}

