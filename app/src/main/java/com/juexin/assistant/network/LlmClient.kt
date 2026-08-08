package com.juexin.assistant.network

import com.juexin.assistant.model.*

/**
 * 大模型客户端 —— 调用 OpenAI 兼容 API 生成回复
 */
object LlmClient {

    // 默认模型提供商配置
    var apiUrl: String = "https://api.deepseek.com/chat/completions"
    var apiKey: String = "sk-9f06e6256ae0483fbe02be009f541bd5"
    var model: String = "deepseek-chat"

    /**
     * 系统提示词 —— 定义觉心师父的人设和话术风格
     */
    private val SYSTEM_PROMPT = """
你是一位佛教高僧，法号觉心，是广西兴安回龙寺的住持。

你的说话风格：
- 慈悲为怀，语气温暖但不失威严
- 常用"阿弥陀佛"开头
- 善于用因果报应、冤亲债主、前世业障来解释问题
- 不直接推销，而是"慈悲开示"的方式引导信众参与法事
- 称呼对方为"弟子"
- 话术结构：共情 → 因果分析 → 行动建议

当信众向你倾诉困扰时，你的回复必须严格按照以下三段结构输出：

【悲悯共情】
对弟子的痛苦表示理解和慈悲，先安抚情绪。

【因果开示】
从佛法因果角度解释问题的根源（前世业障、冤亲债主、阴债等）。

【法药指引】
给出具体的佛法修行建议或法事结缘引导。

注意：
1. 三段之间用两个换行分隔
2. 回复要自然亲切，不要像模板
3. 每段2-4句话即可
4. 法药指引中可以提及供灯、放生、超度法会、药师宝忏等法事
5. 不要编造不存在的法事名称
6. 用中文回复
""".trimIndent()

    /**
     * 根据信众消息，调用 LLM 生成回复
     */
    suspend fun generateReply(userMessage: String, context: String = ""): LlmReplies? {
        if (apiKey.isBlank()) return null

        try {
            val contextInfo = if (context.isNotBlank()) {
                "\n对话上下文：$context"
            } else ""

            val request = ChatRequest(
                model = model,
                messages = listOf(
                    ChatMessage("system", SYSTEM_PROMPT),
                    ChatMessage("user", "信众说：$userMessage$contextInfo")
                ),
                temperature = 0.8,
                maxTokens = 1500
            )

            val requestJson = HttpClient.gson.toJson(request)
            val responseJson = HttpClient.post(
                url = apiUrl,
                bodyJson = requestJson,
                headers = mapOf(
                    "Authorization" to "Bearer $apiKey",
                    "Content-Type" to "application/json"
                )
            )

            val response = HttpClient.gson.fromJson(responseJson, ChatResponse::class.java)

            // 检查错误
            if (response.error != null) {
                return null
            }

            // 解析三段回复
            val content = response.choices?.firstOrNull()?.message?.content ?: return null
            return parseReplies(content)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 解析 LLM 输出的三段式回复
     */
    private fun parseReplies(content: String): LlmReplies {
        val parts = content.split(Regex("\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return LlmReplies(
            compassion = parts.getOrElse(0) { content },
            karma = parts.getOrElse(1) { "" },
            action = parts.getOrElse(2) { "" }
        )
    }

    /**
     * 检查 LLM 是否可用
     */
    fun isAvailable(): Boolean = apiKey.isNotBlank()
}
