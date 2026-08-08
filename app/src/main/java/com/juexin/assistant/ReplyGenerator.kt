package com.juexin.assistant

import android.content.Context
import com.juexin.assistant.model.LlmReplies
import com.juexin.assistant.model.ScriptLibrary
import com.juexin.assistant.model.ScriptTemplate
import com.juexin.assistant.network.LlmClient
import com.juexin.assistant.network.ScriptRepository
import kotlinx.coroutines.*

/**
 * 智能回复生成器 —— 三层回退架构
 *
 * 优先级: 远程话术库 > LLM大模型 > 本地硬编码兜底
 */
object ReplyGenerator {

    private var library: ScriptLibrary? = null
    private var isInitialized = false

    /**
     * 初始化：加载配置 + 同步远程话术库
     */
    suspend fun init(context: Context) {
        if (isInitialized) return
        try {
            com.juexin.assistant.network.AppConfig.load(context)
            // 后台同步话术库
            try {
                library = ScriptRepository.syncFromRemote(context)
            } catch (_: Exception) { }
            isInitialized = true
        } catch (_: Exception) {
            isInitialized = true
        }
    }

    /**
     * 生成回复（核心方法）
     */
    suspend fun generateReply(context: Context, userMessage: String): ReplyResult {
        // 确保已初始化
        if (!isInitialized) {
            try { init(context) } catch (_: Exception) { }
        }

        // 第1层：远程话术库匹配
        library?.let { lib ->
            val matched = ScriptRepository.matchTemplate(lib, userMessage)
            if (matched != null) {
                return ReplyResult(
                    compassion = matched.compassion,
                    karma = matched.karma,
                    action = matched.action,
                    source = ReplySource.REMOTE_SCRIPT
                )
            }
        }

        // 第2层：LLM 大模型生成
        if (LlmClient.isAvailable()) {
            try {
                val llmReply = withTimeout(15000L) {
                    LlmClient.generateReply(userMessage)
                }
                if (llmReply != null) {
                    return ReplyResult(
                        compassion = llmReply.compassion,
                        karma = llmReply.karma,
                        action = llmReply.action,
                        source = ReplySource.LLM
                    )
                }
            } catch (_: Exception) { }
        }

        // 第3层：本地硬编码兜底
        val local = matchLocal(userMessage)
        return ReplyResult(
            compassion = local.compassion,
            karma = local.karma,
            action = local.action,
            source = ReplySource.LOCAL_FALLBACK
        )
    }

    /**
     * 强制同步话术库
     */
    suspend fun forceSync(context: Context): Boolean {
        return try {
            library = ScriptRepository.syncFromRemote(context)
            library != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取当前话术库版本
     */
    fun getLibraryVersion(): Int = library?.version ?: 0

    // ==================== 本地硬编码话术库（兜底） ====================

    private fun matchLocal(message: String): ScriptTemplate {
        val m = message.lowercase()

        // 财运类
        if (m.contains("钱") || m.contains("财") || m.contains("穷") || m.contains("亏") ||
            m.contains("负债") || m.contains("债") || m.contains("赌")
        ) {
            return ScriptTemplate(
                keywords = listOf("钱", "财", "穷", "亏", "负债"),
                compassion = "阿弥陀佛！弟子莫慌。钱财乃身外之物，但世人之苦，多由此生。师父理解你当下的焦虑与无助。",
                karma = "依佛法来看，财运不顺主要有三重因果：一是前世吝啬不肯布施，种下贫穷之因；二是今世冤亲债主讨债，挡你财路；三是祖上阴德有亏，未积福报。",
                action = "师父建议你：一、随缘供灯一盏，照亮财路；二、参加药师宝忏法会，消业除障；三、每月初一十五行善布施，广种福田。"
            )
        }

        // 堕胎类
        if (m.contains("堕胎") || m.contains("流产") || m.contains("打胎") ||
            m.contains("婴灵") || m.contains("孩子没")
        ) {
            return ScriptTemplate(
                keywords = listOf("堕胎", "流产", "婴灵"),
                compassion = "阿弥陀佛！弟子不必过度自责。堕胎虽是业，但你可曾想过，这也许是那孩子与你的缘分未到？",
                karma = "在佛法中，堕胎是杀业。婴灵未能投胎，怨恨难消，会在冥冥中影响你的运势、婚姻、健康，甚至拖累在世子女。",
                action = "师父建议你：尽快为婴灵做一场超度法会，立往生莲位，诵地藏经回向，让婴灵离苦得乐、往生净土。"
            )
        }

        // 婚姻类
        if (m.contains("婚姻") || m.contains("离婚") || m.contains("出轨") ||
            m.contains("老公") || m.contains("老婆") || m.contains("夫妻") ||
            m.contains("感情") || m.contains("分手")
        ) {
            return ScriptTemplate(
                keywords = listOf("婚姻", "离婚", "出轨", "感情"),
                compassion = "阿弥陀佛！一切恩爱会，无常难得久。师父知道你的心在滴血，这是人间最苦的情执之苦。",
                karma = "婚姻不顺，常有前世姻缘债未了，或自己曾破坏他人家庭，今生果报现前。也可能是冤亲债主从中作祟，让夫妻失和。",
                action = "师父建议：一、诚心忏悔前世情债；二、参加药师佛圣诞法会，祈请加持姻缘和顺；三、每月放生回向夫妻和睦。"
            )
        }

        // 子女类
        if (m.contains("孩子") || m.contains("儿子") || m.contains("女儿") ||
            m.contains("不听话") || m.contains("叛逆") || m.contains("学习")
        ) {
            return ScriptTemplate(
                keywords = listOf("孩子", "不听话", "叛逆"),
                compassion = "阿弥陀佛！子女是债，无债不来。师父理解你的心酸，但先别急，我们一起来看看这背后的因果。",
                karma = "孩子不听话、叛逆，往往是前世因果：或是你前世欠这孩子的债未还，或是孩子身上跟着冤亲债主，迷了心窍。",
                action = "师父建议：一、为孩子供长明灯一盏，开启智慧光明；二、参加文昌法会，祈请文殊菩萨加持学业；三、为孩子做冤亲债主超度。"
            )
        }

        // 健康类
        if (m.contains("病") || m.contains("疼") || m.contains("痛") || m.contains("癌") ||
            m.contains("医院") || m.contains("失眠") || m.contains("抑郁")
        ) {
            return ScriptTemplate(
                keywords = listOf("病", "疼", "失眠", "抑郁"),
                compassion = "阿弥陀佛！病来如山倒，师父知道你身心俱疲。但只要是病，就有对治之法。",
                karma = "病有三种：一是四大不调看医生；二是业障病，前世杀业今生还；三是冤亲债主缠身，使人无故生病。",
                action = "师父建议：一、先看医生吃药，不要耽误；二、参加药师宝忏法会，药师佛是东方琉璃世界教主，专治众生疾苦；三、放生培福，回向身体健康。"
            )
        }

        // 噩梦类
        if (m.contains("梦") || m.contains("噩梦") || m.contains("鬼") || m.contains("怕"))
        {
            return ScriptTemplate(
                keywords = listOf("梦", "噩梦", "鬼"),
                compassion = "阿弥陀佛！师父在，莫怕。噩梦缠身，不是小事，这说明你身边确有不好的气场。",
                karma = "噩梦频发，多是冤亲债主托梦讨债，或是你去了不干净的地方，阴气侵体。也可能是过世亲人在地狱受苦，托梦求救。",
                action = "师父建议：一、马上来寺里请一道平安符护身；二、在寺里供一盏地藏灯；三、参加地藏法会，超度冤亲债主。"
            )
        }

        // 亡亲托梦类
        if (m.contains("去世") || m.contains("过世") || m.contains("托梦") ||
            m.contains("亡") || m.contains("死") || m.contains("走")
        ) {
            return ScriptTemplate(
                keywords = listOf("去世", "过世", "托梦"),
                compassion = "阿弥陀佛！亲人离世，心中的痛师父理解。亲人托梦，说明他还在牵挂你，也可能是他在那边过得不好，需要你的帮助。",
                karma = "依《地藏经》所说，亡者七七四十九天内最需要功德回向。如果亡者在饿鬼道或地狱道受苦，就会托梦给阳上亲人，希望得到超度。",
                action = "师父建议：一、立即为亡亲立往生莲位；二、参加地藏法会，诵地藏经回向；三、可以随喜放生、供灯，把功德回向亡亲。"
            )
        }

        // 压力/焦虑类
        if (m.contains("压力") || m.contains("焦虑") || m.contains("烦") ||
            m.contains("累") || m.contains("迷茫")
        ) {
            return ScriptTemplate(
                keywords = listOf("压力", "焦虑", "烦", "迷茫"),
                compassion = "阿弥陀佛！弟子辛苦了。人生在世，谁能无苦？师父看到你这样撑着，心里也不是滋味。",
                karma = "压力大、心不安，往往是因为前世造的业障在今生显现，也可能是冤亲债主在干扰你的心念，让你不得安宁。",
                action = "师父建议：一、先来寺里静心拜佛，让佛光普照内心；二、供一盏药师灯，安神定志；三、参与寺里共修，借助大众力量消除烦恼。"
            )
        }

        // 通用兜底
        return ScriptTemplate(
            keywords = listOf("*"),
            compassion = "阿弥陀佛！弟子所遇之事，师父已明了。人生不易，能在茫茫苦海中遇到佛法，本身就是莫大的福报。",
            karma = "世间一切不顺，根源皆在因果。前世所造之业，今生必受其报。但不必绝望，因为因果是可以转的，业障是可以消的。",
            action = "师父建议弟子：来寺里一趟，师父当面为你开示。或者随缘参加寺院的法会，先供一盏佛前灯，让佛光为你照亮前路。"
        )
    }
}

/**
 * 回复结果
 */
data class ReplyResult(
    val compassion: String,
    val karma: String,
    val action: String,
    val source: ReplySource
)

/**
 * 回复来源
 */
enum class ReplySource {
    REMOTE_SCRIPT,  // 远程同步的话术库
    LLM,            // 大模型生成
    LOCAL_FALLBACK  // 本地硬编码兜底
}
