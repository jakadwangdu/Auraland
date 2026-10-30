package dev.aura.island

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Whitelisted phone actions. Used by the island quick menu and by the AI assistant. */
object Quick {
    val ACTIONS = setOf("torch", "open_app", "scanner", "camera", "calculator", "timer", "alarm",
        "search", "dial", "navigate", "settings", "battery", "time")

    @Volatile private var torchOn = false
    private var watching = false

    fun torchIsOn(): Boolean = torchOn

    private fun start(c: Context, i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        c.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }

    fun torch(c: Context, mode: String): String = try {
        val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        if (!watching) {
            watching = true
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { torchOn = enabled }
            }, null)
        }
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) {
            "No flashlight found"
        } else {
            val want = when (mode) { "on" -> true; "off" -> false; else -> !torchOn }
            cm.setTorchMode(id, want)
            torchOn = want
            if (want) "Torch on" else "Torch off"
        }
    } catch (e: Exception) {
        "Could not use the torch"
    }

    fun scanner(c: Context): String {
        val pm = c.packageManager
        for (p in listOf("com.xiaomi.scanner", "com.google.ar.lens", "com.google.android.apps.lens")) {
            val i = pm.getLaunchIntentForPackage(p)
            if (i != null && start(c, i)) return "Scanner opened"
        }
        return if (start(c, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) "No scanner app found, opened the camera" else "No scanner available"
    }

    fun camera(c: Context): String =
        if (start(c, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) "Camera opened" else "No camera app found"

    fun calculator(c: Context): String {
        val i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
        return if (start(c, i)) "Calculator opened" else openApp(c, "calculator")
    }

    fun apps(c: Context): List<Pair<String, String>> {
        val pm = c.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(q, 0)
            .map { Pair(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.second != c.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
    }

    fun launchPkg(c: Context, pkg: String): Boolean {
        val i = c.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return start(c, i)
    }

    fun openApp(c: Context, name: String): String {
        val n = name.trim().lowercase()
        if (n.isEmpty()) return "Which app?"
        val list = apps(c)
        val hit = list.firstOrNull { it.first.lowercase() == n }
            ?: list.firstOrNull { it.first.lowercase().startsWith(n) }
            ?: list.firstOrNull { it.first.lowercase().contains(n) }
            ?: return "I couldn't find an app called $name"
        return if (launchPkg(c, hit.second)) "Opening ${hit.first}" else "Couldn't open ${hit.first}"
    }

    private fun fmt(s: Int): String =
        if (s % 3600 == 0) "${s / 3600} h" else if (s % 60 == 0) "${s / 60} min" else "$s sec"

    fun timer(c: Context, secs: Int): String {
        if (secs < 1 || secs > 86400) return "A timer must be between 1 second and 24 hours"
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, secs)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (start(c, i)) "Timer set for ${fmt(secs)}" else "No clock app can set a timer"
    }

    fun alarm(c: Context, hour: Int, minute: Int): String {
        if (hour !in 0..23 || minute !in 0..59) return "That time doesn't look right"
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (start(c, i)) String.format(Locale.US, "Alarm set for %02d:%02d", hour, minute) else "No clock app can set an alarm"
    }

    fun search(c: Context, q: String): String {
        if (q.isBlank()) return "What should I search for?"
        val u = Uri.parse("https://www.google.com/search?q=" + Uri.encode(q.take(200)))
        return if (start(c, Intent(Intent.ACTION_VIEW, u))) "Searching for $q" else "No browser found"
    }

    /** Only opens the dialer with the number filled in. It never places a call. */
    fun dial(c: Context, number: String): String {
        val n = number.filter { it.isDigit() || it == '+' }
        if (n.length < 3) return "That number looks too short"
        return if (start(c, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n")))) "Dialer ready with $n" else "No dialer found"
    }

    fun navigate(c: Context, place: String): String {
        if (place.isBlank()) return "Where to?"
        val u = Uri.parse("geo:0,0?q=" + Uri.encode(place.take(120)))
        return if (start(c, Intent(Intent.ACTION_VIEW, u))) "Showing $place on the map" else "No maps app found"
    }

    fun settings(c: Context, page: String): String {
        val a = when (page.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            else -> return "I can open wifi, bluetooth, display or battery settings"
        }
        return if (start(c, Intent(a))) "Opening $page settings" else "Couldn't open settings"
    }

    fun battery(c: Context): String {
        val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "Battery info unavailable"
        val pct = b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / b.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        return "Battery $pct%" + (if (charging) ", charging" else "")
    }

    fun now(): String = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date())

    fun run(c: Context, name: String, a: JSONObject): String = when (name) {
        "torch" -> torch(c, a.optString("mode", "toggle"))
        "open_app" -> openApp(c, a.optString("name"))
        "scanner" -> scanner(c)
        "camera" -> camera(c)
        "calculator" -> calculator(c)
        "timer" -> timer(c, a.optInt("seconds", 0))
        "alarm" -> alarm(c, a.optInt("hour", -1), a.optInt("minute", 0))
        "search" -> search(c, a.optString("query"))
        "dial" -> dial(c, a.optString("number"))
        "navigate" -> navigate(c, a.optString("place"))
        "settings" -> settings(c, a.optString("page"))
        "battery" -> battery(c)
        "time" -> now()
        else -> "I can't do that yet"
    }
}
