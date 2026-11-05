package gr.wsol.gamenight.ai

import gr.wsol.gamenight.extras.Report
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The optional AI, as in Binelik AI: the user's own key (Gemini, Groq and OpenRouter have free
 * tiers), no server of ours. Every game works without it.
 */
enum class Provider(val label: String, val keyPage: String, val keyPattern: Regex, val model: String = "", val chatUrl: String = "", val testUrl: String = "", val free: Boolean = false) {
    GEMINI("Gemini", "https://aistudio.google.com/apikey", Regex("AIza[0-9A-Za-z_\\-]{30,45}"), free = true, testUrl = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1"),
    GROQ("Groq", "https://console.groq.com/keys", Regex("gsk_[0-9A-Za-z]{40,64}"), "openai/gpt-oss-120b", "https://api.groq.com/openai/v1/chat/completions", "https://api.groq.com/openai/v1/models", free = true),
    OPENROUTER("OpenRouter", "https://openrouter.ai/settings/keys", Regex("sk-or-v1-[0-9a-f]{64}"), "google/gemma-4-31b-it:free", "https://openrouter.ai/api/v1/chat/completions", "https://openrouter.ai/api/v1/key", free = true),
    DEEPSEEK("DeepSeek", "https://platform.deepseek.com/api_keys", Regex("sk-[0-9A-Za-z]{20,64}"), "deepseek-chat", "https://api.deepseek.com/chat/completions", "https://api.deepseek.com/models"),
    ;

    /** The key inside a copied text (people often copy a space or a line too). */
    fun findKey(text: String?): String? = text?.let { keyPattern.find(it)?.value }
}

sealed class AiResult {
    data class Ok(val text: String) : AiResult()
    data class Fail(val reason: Reason, val detail: String = "") : AiResult()
}

enum class Reason { BAD_KEY, LIMIT, BLOCKED, NETWORK, BUSY, MODEL, OTHER }

object Ai {
    const val GEMINI_MODEL = "gemini-flash-latest"
    const val GEMINI_FALLBACK = "gemini-flash-lite-latest"
    private val json = Json { ignoreUnknownKeys = true }

    fun geminiBody(system: String, prompt: String, think: Boolean = true): JsonObject = buildJsonObject {
        put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) }) })
        put("contents", buildJsonArray { add(buildJsonObject { put("role", "user"); put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) }) }) })
        put("generationConfig", buildJsonObject {
            put("temperature", 1.1); put("maxOutputTokens", 2048)
            if (think) put("thinkingConfig", buildJsonObject { put("thinkingLevel", "low") })
        })
    }

    fun chatBody(provider: Provider, system: String, prompt: String): JsonObject = buildJsonObject {
        put("model", provider.model)
        put("messages", buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", system) })
            add(buildJsonObject { put("role", "user"); put("content", prompt) })
        })
        put("temperature", 1.0)
        if (provider == Provider.GROQ) { put("max_tokens", 2048); put("reasoning_effort", "low") } else put("max_tokens", 600)
    }

    fun geminiText(body: String): AiResult {
        val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return AiResult.Fail(Reason.OTHER)
        (o["promptFeedback"] as? JsonObject)?.get("blockReason")?.jsonPrimitive?.contentOrNull?.let { return AiResult.Fail(Reason.BLOCKED, it) }
        val cand = (o["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return AiResult.Fail(Reason.OTHER)
        val text = (cand["content"] as? JsonObject)?.get("parts")?.jsonArray?.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }?.joinToString("")?.trim().orEmpty()
        return if (text.isNotEmpty()) AiResult.Ok(text) else AiResult.Fail(Reason.OTHER, cand["finishReason"]?.jsonPrimitive?.contentOrNull.orEmpty())
    }

    fun chatText(body: String): AiResult {
        val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return AiResult.Fail(Reason.OTHER)
        val text = (o["choices"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        return if (text.isNotEmpty()) AiResult.Ok(text) else AiResult.Fail(Reason.OTHER)
    }

    fun reasonFor(code: Int): Reason = when (code) { 400, 401, 403 -> Reason.BAD_KEY; 402, 429 -> Reason.LIMIT; 500, 502, 503, 504 -> Reason.BUSY; 404 -> Reason.MODEL; else -> Reason.OTHER }

    @Volatile private var noThink = false

    /** One question, one answer. Network drops and an overloaded model are retried twice. */
    suspend fun ask(provider: Provider, key: String, system: String, prompt: String): AiResult {
        var model = GEMINI_MODEL
        var r = once(provider, key, system, prompt, model)
        if (provider == Provider.GEMINI && !noThink && r is AiResult.Fail && r.detail == "HTTP 400") { noThink = true; r = once(provider, key, system, prompt, model) }
        for (wait in listOf(1000L, 2000L)) {
            if (r !is AiResult.Fail || (r.reason != Reason.NETWORK && r.reason != Reason.BUSY)) break
            delay(wait)
            if (r.reason == Reason.BUSY) model = GEMINI_FALLBACK
            r = once(provider, key, system, prompt, model)
        }
        if (r is AiResult.Fail) Report.note(provider.label, "${r.reason} ${r.detail}")
        return r
    }

    private suspend fun once(provider: Provider, key: String, system: String, prompt: String, model: String): AiResult = withContext(Dispatchers.IO) {
        val (url, body) = when (provider) {
            Provider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent" to geminiBody(system, prompt, !noThink)
            else -> provider.chatUrl to chatBody(provider, system, prompt)
        }
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 40_000; c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (provider == Provider.GEMINI) c.setRequestProperty("x-goog-api-key", key) else c.setRequestProperty("Authorization", "Bearer $key")
            if (provider == Provider.OPENROUTER) c.setRequestProperty("X-Title", "Gamenight GR")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            if (code !in 200..299) { c.disconnect(); return@withContext AiResult.Fail(reasonFor(code), "HTTP $code") }
            val text = c.inputStream.bufferedReader().use { it.readText() }; c.disconnect()
            if (provider == Provider.GEMINI) geminiText(text) else chatText(text)
        } catch (e: IOException) { AiResult.Fail(Reason.NETWORK, e.message.orEmpty()) }
    }

    /** Gemini body with a picture (PNG) next to the question. */
    fun geminiImageBody(system: String, prompt: String, png: String): JsonObject = buildJsonObject {
        put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) }) })
        put("contents", buildJsonArray { add(buildJsonObject { put("role", "user"); put("parts", buildJsonArray {
            add(buildJsonObject { put("inline_data", buildJsonObject { put("mime_type", "image/png"); put("data", png) }) })
            add(buildJsonObject { put("text", prompt) })
        }) }) })
        put("generationConfig", buildJsonObject { put("temperature", 0.4); put("maxOutputTokens", 256); put("thinkingConfig", buildJsonObject { put("thinkingLevel", "low") }) })
    }

    /**
     * 1.1, «Ζωγράφισε για το AI»: what is in [png] (base64). Only Gemini sees pictures here; the
     * other providers give [Reason.MODEL].
     */
    suspend fun askImage(provider: Provider, key: String, system: String, prompt: String, png: String): AiResult = withContext(Dispatchers.IO) {
        if (provider != Provider.GEMINI) return@withContext AiResult.Fail(Reason.MODEL, "image")
        try {
            val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent").openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 40_000; c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("x-goog-api-key", key)
            c.outputStream.use { it.write(geminiImageBody(system, prompt, png).toString().toByteArray()) }
            val code = c.responseCode
            if (code !in 200..299) { c.disconnect(); return@withContext AiResult.Fail(reasonFor(code), "HTTP $code") }
            val text = c.inputStream.bufferedReader().use { it.readText() }; c.disconnect()
            geminiText(text)
        } catch (e: IOException) { AiResult.Fail(Reason.NETWORK, e.message.orEmpty()) }
    }

    suspend fun testKey(provider: Provider, key: String): AiResult = withContext(Dispatchers.IO) {
        try {
            val c = URL(provider.testUrl).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 20_000
            if (provider == Provider.GEMINI) c.setRequestProperty("x-goog-api-key", key) else c.setRequestProperty("Authorization", "Bearer $key")
            val code = c.responseCode; c.disconnect()
            if (code in 200..299) AiResult.Ok("") else AiResult.Fail(reasonFor(code), "HTTP $code")
        } catch (e: IOException) { AiResult.Fail(Reason.NETWORK, e.message.orEmpty()) }
    }
}
