package com.juexin.assistant.model

import com.google.gson.annotations.SerializedName

/**
 * OpenAI 兼容 Chat Completions 请求
 */
data class ChatRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<ChatMessage>,
    @SerializedName("temperature") val temperature: Double = 0.8,
    @SerializedName("max_tokens") val maxTokens: Int = 2000,
    @SerializedName("stream") val stream: Boolean = false
)

data class ChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

/**
 * OpenAI 兼容 Chat Completions 响应
 */
data class ChatResponse(
    @SerializedName("id") val id: String? = null,
    @SerializedName("choices") val choices: List<Choice>? = null,
    @SerializedName("error") val error: ApiError? = null
)

data class Choice(
    @SerializedName("index") val index: Int = 0,
    @SerializedName("message") val message: ChatMessage? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class ApiError(
    @SerializedName("message") val message: String? = null,
    @SerializedName("type") val type: String? = null
)

/**
 * LLM 回复解析结果
 */
data class LlmReplies(
    val compassion: String,
    val karma: String,
    val action: String
)
