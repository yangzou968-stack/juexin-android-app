package com.juexin.assistant.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 应用配置管理（DataStore 持久化）
 */
object AppConfig {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "juexin_config")

    // 配置键
    private val KEY_API_URL = stringPreferencesKey("api_url")
    private val KEY_API_KEY = stringPreferencesKey("api_key")
    private val KEY_MODEL = stringPreferencesKey("model")
    private val KEY_SCRIPTS_URL = stringPreferencesKey("scripts_url")
    private val KEY_VERSION_URL = stringPreferencesKey("version_url")
    private val KEY_USE_LLM = booleanPreferencesKey("use_llm")
    private val KEY_AUTO_SYNC = booleanPreferencesKey("auto_sync")

    /**
     * 从 DataStore 加载所有配置
     */
    suspend fun load(context: Context) {
        val prefs = context.dataStore.data.first()

        LlmClient.apiUrl = prefs[KEY_API_URL] ?: LlmClient.apiUrl
        LlmClient.apiKey = prefs[KEY_API_KEY] ?: LlmClient.apiKey
        LlmClient.model = prefs[KEY_MODEL] ?: LlmClient.model

        val scriptsUrl = prefs[KEY_SCRIPTS_URL]
        if (!scriptsUrl.isNullOrBlank()) ScriptRepository.scriptsUrl = scriptsUrl

        val versionUrl = prefs[KEY_VERSION_URL]
        if (!versionUrl.isNullOrBlank()) ScriptRepository.versionUrl = versionUrl
    }

    /**
     * 保存 LLM API 配置
     */
    suspend fun saveLlmConfig(context: Context, apiUrl: String, apiKey: String, model: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_API_URL] = apiUrl
            prefs[KEY_API_KEY] = apiKey
            prefs[KEY_MODEL] = model
        }
        LlmClient.apiUrl = apiUrl
        LlmClient.apiKey = apiKey
        LlmClient.model = model
    }

    /**
     * 保存话术库配置
     */
    suspend fun saveScriptConfig(context: Context, scriptsUrl: String, versionUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SCRIPTS_URL] = scriptsUrl
            prefs[KEY_VERSION_URL] = versionUrl
        }
        ScriptRepository.scriptsUrl = scriptsUrl
        ScriptRepository.versionUrl = versionUrl
    }

    /**
     * 获取是否启用 LLM
     */
    suspend fun isLlmEnabled(context: Context): Boolean {
        return context.dataStore.data.first()[KEY_USE_LLM] ?: true
    }

    /**
     * 设置是否启用 LLM
     */
    suspend fun setLlmEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_LLM] = enabled
        }
    }

    /**
     * 获取是否自动同步话术库
     */
    suspend fun isAutoSync(context: Context): Boolean {
        return context.dataStore.data.first()[KEY_AUTO_SYNC] ?: true
    }

    /**
     * 设置是否自动同步
     */
    suspend fun setAutoSync(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_SYNC] = enabled
        }
    }
}
