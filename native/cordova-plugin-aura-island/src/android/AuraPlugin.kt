package dev.aura.island

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.json.JSONArray
import org.json.JSONObject

class AuraPlugin : CordovaPlugin() {
    private val ctx: Context get() = cordova.activity.applicationContext
    private val prefs get() = ctx.getSharedPreferences("aura", Context.MODE_PRIVATE)

    override fun execute(action: String, args: JSONArray, cb: CallbackContext): Boolean {
        when (action) {
            "status" -> cb.success(JSONObject().apply {
                put("overlay", Settings.canDrawOverlays(ctx))
                put("notifications", NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName))
                put("running", OverlayService.running)
                put("hidePreview", prefs.getBoolean("hide_preview", false))
            })
            "requestOverlay" -> {
                launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                cb.success()
            }
            "openNotificationAccess" -> {
                launch(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); cb.success()
            }
            "openBatterySettings" -> {
                launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))); cb.success()
            }
            "start" -> {
                if (!Settings.canDrawOverlays(ctx)) { cb.error("Grant overlay permission first"); return true }
                try {
                    val i = Intent(ctx, OverlayService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
                    prefs.edit().putBoolean("enabled", true).apply()
                    cb.success()
                } catch (e: Exception) { cb.error(e.message ?: "Could not start island") }
            }
            "stop" -> {
                ctx.stopService(Intent(ctx, OverlayService::class.java))
                prefs.edit().putBoolean("enabled", false).apply()
                cb.success()
            }
            "setHidePreview" -> {
                prefs.edit().putBoolean("hide_preview", args.optBoolean(0)).apply(); cb.success()
            }
            "test" -> {
                if (!OverlayService.running) cb.error("Enable the island first")
                else { OverlayService.post("Aura Island", "Test notification", 2); cb.success() }
            }
            else -> return false
        }
        return true
    }

    private fun launch(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        cordova.activity.startActivity(i)
    }
}
