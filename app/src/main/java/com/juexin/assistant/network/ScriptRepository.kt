package com.juexin.assistant.network

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.reflect.TypeToken
import com.juexin.assistant.model.ScriptLibrary
import com.juexin.assistant.model.ScriptTemplate
import com.juexin.assistant.model.VersionCheck
import kotlinx.coroutines.*

/**
 * 话术库仓库 —— 远程同步 + 本地缓存
 */
object ScriptRepository {

    private const val PREFS_NAME = "juexin_scripts"
    private const val KEY_CACHED_SCRIPTS = "cached_scripts_json"
    private const val KEY_CACHED_VERSION = "cached_version"
    private const val KEY_LAST_SYNC = "last_sync_time"

    // 默认远程地址（GitHub Raw）
    private const val DEFAULT_VERSION_URL =
        "https://raw.githubusercontent.com/yangzou968-stack/juexin-android-app/main/scripts/version.json"
    private const val DEFAULT_SCRIPTS_URL =
        "https://raw.githubusercontent.com/yangzou968-stack/juexin-android-app/main/scripts/library.json"

    var versionUrl = DEFAULT_VERSION_URL
    var scriptsUrl = DEFAULT_SCRIPTS_URL

    private var cachedLibrary: ScriptLibrary? = null
    private var syncJob: Job? = null

    /**
     * 获取话术库（优先本地缓存）
     */
    suspend fun getLibrary(context: Context): ScriptLibrary? {
        cachedLibrary?.let { return it }

        // 尝试从 SharedPreferences 加载缓存
        loadFromCache(context)?.let {
            cachedLibrary = it
            return it
        }

        // 无缓存，尝试远程拉取
        return try {
            syncFromRemote(context)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 检查并同步最新话术库
     */
    suspend fun syncFromRemote(context: Context): ScriptLibrary? {
        try {
            // 1. 检查版本
            val versionJson = HttpClient.get(versionUrl)
            val versionCheck = HttpClient.gson.fromJson(versionJson, VersionCheck::class.java)

            val cachedVersion = getPrefs(context).getInt(KEY_CACHED_VERSION, 0)

            if (versionCheck.latestVersion <= cachedVersion && cachedLibrary != null) {
                return cachedLibrary
            }

            // 2. 下载最新话术库
            val libraryUrl = versionCheck.downloadUrl.ifEmpty { scriptsUrl }
            val libraryJson = HttpClient.get(libraryUrl)
            val library = HttpClient.gson.fromJson(libraryJson, ScriptLibrary::class.java)

            // 3. 缓存到本地
            saveToCache(context, library)
            cachedLibrary = library

            return library
        } catch (e: Exception) {
            // 同步失败，返回已有缓存
            return cachedLibrary ?: loadFromCache(context)
        }
    }

    /**
     * 后台静默同步
     */
    fun syncInBackground(context: Context, scope: CoroutineScope) {
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            try {
                syncFromRemote(context)
            } catch (_: Exception) { }
        }
    }

    /**
     * 通过对话内容匹配话术模板
     */
    fun matchTemplate(library: ScriptLibrary, userMessage: String): ScriptTemplate? {
        for (template in library.templates) {
            for (keyword in template.keywords) {
                if (userMessage.contains(keyword)) {
                    return template
                }
            }
        }
        return null
    }

    private fun loadFromCache(context: Context): ScriptLibrary? {
        return try {
            val json = getPrefs(context).getString(KEY_CACHED_SCRIPTS, null) ?: return null
            HttpClient.gson.fromJson(json, ScriptLibrary::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveToCache(context: Context, library: ScriptLibrary) {
        val json = HttpClient.gson.toJson(library)
        getPrefs(context).edit()
            .putString(KEY_CACHED_SCRIPTS, json)
            .putInt(KEY_CACHED_VERSION, library.version)
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
    }

    fun getLastSyncTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_SYNC, 0)
    }

    fun getCachedVersion(context: Context): Int {
        return getPrefs(context).getInt(KEY_CACHED_VERSION, 0)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
