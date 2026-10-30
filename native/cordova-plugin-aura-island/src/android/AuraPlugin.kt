package dev.aura.island

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.json.JSONArray
import org.json.JSONObject

class AuraPlugin : CordovaPlugin() {
    private val ctx: Context get() = cordova.activity.applicationContext
    private val prefs get() = ctx.getSharedPreferences("aura", Context.MODE_PRIVATE)

    private fun listenerOn(): Boolean {
        val s = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        return s != null && s.contains(ctx.packageName)
    }

    private fun pos(): JSONObject =
        JSONObject().put("x", prefs.getInt("pos_x", 0)).put("y", prefs.getInt("pos_y", 8))

    override fun execute(action: String, args: JSONArray, cb: CallbackContext): Boolean {
        when (action) {
            "status" -> cb.success(JSONObject().apply {
                put("overlay", Settings.canDrawOverlays(ctx))
                put("notifications", listenerOn())
                put("running", OverlayService.running)
                put("hidePreview", prefs.getBoolean("hide_preview", false))
            })
            "requestOverlay" -> {
                launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                cb.success()
            }
            "openNotificationAccess" -> {
                launch(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                cb.success()
            }
            "openBatterySettings" -> {
                launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                cb.success()
            }
            "start" -> {
                if (!Settings.canDrawOverlays(ctx)) {
                    cb.error("Grant overlay permission first")
                } else {
                    try {
                        val i = Intent(ctx, OverlayService::class.java)
                        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
                        prefs.edit().putBoolean("enabled", true).apply()
                        cb.success()
                    } catch (e: Exception) {
                        cb.error(e.message ?: "Could not start island")
                    }
                }
            }
            "stop" -> {
                ctx.stopService(Intent(ctx, OverlayService::class.java))
                prefs.edit().putBoolean("enabled", false).apply()
                cb.success()
            }
            "setHidePreview" -> {
                prefs.edit().putBoolean("hide_preview", args.optBoolean(0)).apply()
                cb.success()
            }
            "test" -> {
                if (!OverlayService.running) {
                    cb.error("Enable the island first")
                } else {
                    OverlayService.post("Aura Island", "Test notification", 2)
                    cb.success()
                }
            }
            "setPosition" -> {
                val x = args.optInt(0).coerceIn(-400, 400)
                val y = args.optInt(1).coerceIn(0, 200)
                prefs.edit().putInt("pos_x", x).putInt("pos_y", y).apply()
                OverlayService.reloadPos()
                cb.success()
            }
            "getPosition" -> cb.success(pos())
            "autoAlign" -> OverlayService.autoAlign { err ->
                if (err == null) cb.success(pos()) else cb.error(err)
            }
            "listApps" -> cordova.threadPool.execute {
                val a = JSONArray()
                for (p in Quick.apps(ctx)) a.put(JSONObject().put("label", p.first).put("pkg", p.second))
                cb.success(a)
            }
            "setShortcuts" -> {
                val clean = try { JSONArray(args.optString(0, "[]")).toString() } catch (e: Exception) { "[]" }
                prefs.edit().putString("shortcuts", clean).apply()
                cb.success()
            }
            "getShortcuts" -> cb.success(prefs.getString("shortcuts", "[]") ?: "[]")
            "ai" -> cordova.threadPool.execute {
                val r = try { AiEngine.handle(ctx, args.optString(0)) } catch (e: Exception) { "Something went wrong." }
                cb.success(r)
            }
            "setAi" -> {
                prefs.edit().putString("ai_url", args.optString(0)).putString("ai_model", args.optString(1)).apply()
                cb.success()
            }
            "getAi" -> cb.success(JSONObject()
                .put("url", prefs.getString("ai_url", "") ?: "")
                .put("model", prefs.getString("ai_model", "") ?: ""))
            else -> return false
        }
        return true
    }

    private fun launch(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        cordova.activity.startActivity(i)
    }
}
