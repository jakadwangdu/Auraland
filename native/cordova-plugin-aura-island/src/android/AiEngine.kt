package dev.aura.island

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Assistant brain. Phone commands are matched locally (works offline, English and Hinglish).
 * Everything else goes to a free, key-less open model; any action the model asks for is checked
 * against a fixed whitelist before it runs. Model text is never executed as code.
 */
object AiEngine {
    private const val DEFAULT_URL = "https://text.pollinations.ai/openai"
    private const val DEFAULT_MODEL = "mistral"
    private val history = ArrayList<Pair<String, String>>()

    private const val SYSTEM =
        "You are Aura, a tiny assistant inside an Android overlay called Aura Island. " +
        "You understand English, Hindi and Hinglish; reply in the user's language in at most 3 short sentences. " +
        "If the user wants a phone task, reply ONLY with JSON like " +
        "{\"reply\":\"short confirmation\",\"action\":{\"name\":\"torch\",\"args\":{\"mode\":\"on\"}}}. " +
        "Allowed actions: torch{mode:on|off|toggle}, open_app{name}, scanner, camera, calculator, timer{seconds}, " +
        "alarm{hour,minute}, search{query}, dial{number}, navigate{place}, settings{page:wifi|bluetooth|display|battery}, battery, time. " +
        "Otherwise reply with plain text only."

    fun handle(c: Context, text: String): String {
        val t = text.trim().take(500)
        if (t.isEmpty()) return ""
        val l = local(c, t)
        if (l != null) return l
        return llm(c, t)
    }

    private fun local(c: Context, raw: String): String? {
        val s = raw.lowercase().trim().trimEnd('.', '!', '?')
        fun has(vararg k: String): Boolean = k.any { s.contains(it) }

        if (s.contains("timer")) {
            val m = Regex("""(\d+)\s*(second|sec|minute|min|minat|hour|hr|ghante|ghanta)""").find(s)
            if (m != null) {
                val n = (m.groupValues[1].toIntOrNull() ?: 0).coerceAtMost(100000)
                val u = m.groupValues[2]
                val mult = if (u.startsWith("sec")) 1 else if (u.startsWith("h") || u.startsWith("g")) 3600 else 60
                return Quick.timer(c, n * mult)
            }
        }
        if (s.contains("alarm")) {
            val m = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""").find(s)
            if (m != null) {
                var h = m.groupValues[1].toInt()
                val min = m.groupValues[2].ifEmpty { "0" }.toInt()
                val ap = m.groupValues[3]
                if (ap == "pm" && h < 12) h += 12
                if (ap == "am" && h == 12) h = 0
                return Quick.alarm(c, h, min)
            }
        }
        val call = Regex("""^(?:call|dial)\s+([+\d][\d\s-]{2,})$""").find(s)
        if (call != null) return Quick.dial(c, call.groupValues[1])
        val open = Regex("""^(?:open|launch|start|kholo|khol do|khol)\s+(.+)$""").find(s)
        if (open != null) return Quick.openApp(c, open.groupValues[1])
        val nav = Regex("""^(?:navigate to|directions to|take me to)\s+(.+)$""").find(s)
        if (nav != null) return Quick.navigate(c, nav.groupValues[1])
        val find = Regex("""^(?:search|google|find)\s+(?:for\s+)?(.+)$""").find(s)
        if (find != null) return Quick.search(c, find.groupValues[1])

        if (has("torch", "flashlight", "flash light", "टॉर्च")) {
            val off = Regex("""\b(off|band|bandh)\b""").containsMatchIn(s) || s.contains("बंद")
            val on = Regex("""\b(on|jala|jalao|chalu)\b""").containsMatchIn(s)
            return Quick.torch(c, if (off) "off" else if (on) "on" else "toggle")
        }
        if (has("battery", "बैटरी")) return Quick.battery(c)
        if (has("scan", "qr")) return Quick.scanner(c)
        if (has("camera", "कैमरा")) return Quick.camera(c)
        if (has("calculator", "calc")) return Quick.calculator(c)
        if (has("wifi", "wi-fi")) return Quick.settings(c, "wifi")
        if (has("bluetooth")) return Quick.settings(c, "bluetooth")
        if (s.length <= 30 && Regex("""\b(time|date|samay|waqt|tarikh)\b""").containsMatchIn(s)) return Quick.now()
        return null
    }

    private fun llm(c: Context, text: String): String {
        val p = c.getSharedPreferences("aura", Context.MODE_PRIVATE)
        val url = p.getString("ai_url", null)?.takeIf { it.startsWith("https://") } ?: DEFAULT_URL
        val model = p.getString("ai_model", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", SYSTEM))
        synchronized(history) {
            for (h in history.takeLast(6)) msgs.put(JSONObject().put("role", h.first).put("content", h.second))
        }
        msgs.put(JSONObject().put("role", "user").put("content", text))
        val body = JSONObject().put("model", model).put("messages", msgs)
            .put("temperature", 0.4).put("max_tokens", 220).toString()

        val raw = try { post(url, body) } catch (e: Exception) {
            return "I can't reach the AI right now. Commands like torch, open <app>, timer 5 min or battery still work."
        }
        val out = try {
            JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        } catch (e: Exception) {
            return "The AI sent an answer I couldn't read. Please try again."
        }
        val reply = act(c, out)
        synchronized(history) {
            history.add(Pair("user", text))
            history.add(Pair("assistant", reply))
            while (history.size > 12) history.removeAt(0)
        }
        return reply
    }

    private fun act(c: Context, out: String): String {
        val a = out.indexOf('{')
        val b = out.lastIndexOf('}')
        if (a < 0 || b <= a) return out
        return try {
            val j = JSONObject(out.substring(a, b + 1))
            val reply = j.optString("reply", "")
            val ac = j.optJSONObject("action")
            val name = ac?.optString("name", "") ?: ""
            if (name in Quick.ACTIONS) {
                Quick.run(c, name, ac?.optJSONObject("args") ?: JSONObject())
            } else if (reply.isNotBlank()) {
                reply
            } else {
                out
            }
        } catch (e: Exception) {
            out
        }
    }

    private fun post(url: String, body: String): String {
        val h = URL(url).openConnection() as HttpURLConnection
        try {
            h.requestMethod = "POST"
            h.connectTimeout = 12000
            h.readTimeout = 30000
            h.doOutput = true
            h.setRequestProperty("Content-Type", "application/json")
            h.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (h.responseCode !in 200..299) throw java.io.IOException("HTTP " + h.responseCode)
            return h.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            h.disconnect()
        }
    }
}
