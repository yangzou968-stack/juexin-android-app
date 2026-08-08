package com.juexin.assistant.model

import com.google.gson.annotations.SerializedName

/**
 * 远程话术库根模型
 */
data class ScriptLibrary(
    @SerializedName("version") val version: Int,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("templates") val templates: List<ScriptTemplate>,
    @SerializedName("generic") val generic: ScriptTemplate
)

/**
 * 单个场景话术模板
 */
data class ScriptTemplate(
    @SerializedName("keywords") val keywords: List<String>,
    @SerializedName("compassion") val compassion: String,
    @SerializedName("karma") val karma: String,
    @SerializedName("action") val action: String
)

/**
 * 话术库更新检查响应
 */
data class VersionCheck(
    @SerializedName("latest_version") val latestVersion: Int,
    @SerializedName("download_url") val downloadUrl: String,
    @SerializedName("changelog") val changelog: String
)
