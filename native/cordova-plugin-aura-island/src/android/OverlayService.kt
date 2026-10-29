package dev.aura.island

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Draws the island with a real native overlay window (TYPE_APPLICATION_OVERLAY). */
class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private var root: FrameLayout? = null
    private lateinit var bg: GradientDrawable
    private lateinit var content: LinearLayout
    private lateinit var dot: View
    private lateinit var title: TextView
    private lateinit var sub: TextView
    private val main = Handler(Looper.getMainLooper())
    private var anim: ValueAnimator? = null
    private var progress = 0f
    private var expanded = false
    private var showingPrio = 0
    private var landscape = false
    private val autoCollapse = Runnable { setExpanded(false) }

    private val power = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_POWER_CONNECTED) {
                val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val pct = b?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100) } ?: 0
                show("Charging", "$pct%", 1)
            }
        }
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildView()
        applyOrientation(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        registerReceiver(power, IntentFilter(Intent.ACTION_POWER_CONNECTED))
        running = true
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel("aura", "Island status", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "aura").setContentTitle("Aura Island is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    private fun buildView() {
        bg = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = dp(17f) }
        val r = FrameLayout(this).apply { background = bg; setOnClickListener { setExpanded(!expanded) } }
        title = TextView(this).apply { setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = Typeface.DEFAULT_BOLD; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        sub = TextView(this).apply { setTextColor(Color.parseColor("#A3A3A3")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; alpha = 0f
            setPadding(dp(18f).toInt(), dp(14f).toInt(), dp(18f).toInt(), dp(10f).toInt())
            addView(title); addView(sub)
        }
        dot = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#00FFFB")) }
        }
        r.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        r.addView(dot, FrameLayout.LayoutParams(dp(8f).toInt(), dp(8f).toInt(), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(14f).toInt() })
        lp = WindowManager.LayoutParams(
            dp(130f).toInt(), dp(34f).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(8f).toInt() }
        wm.addView(r, lp)
        root = r
    }

    private fun render(p: Float) {
        val r = root ?: return
        progress = p
        val maxW = (resources.displayMetrics.widthPixels - dp(24f)).coerceAtMost(dp(340f))
        lp.width = (dp(130f) + (maxW - dp(130f)) * p).toInt()
        lp.height = (dp(34f) + (dp(92f) - dp(34f)) * p).toInt()
        bg.cornerRadius = dp(17f) + (dp(28f) - dp(17f)) * p
        content.alpha = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
        dot.alpha = 1f - p
        try { wm.updateViewLayout(r, lp) } catch (_: Exception) {}
    }

    private fun setExpanded(e: Boolean) {
        expanded = e
        main.removeCallbacks(autoCollapse)
        anim?.cancel()                           // cancel obsolete animation
        if (!e) showingPrio = 0
        anim = ValueAnimator.ofFloat(progress, if (e) 1f else 0f).apply {
            duration = if (e) 320L else 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener { render(it.animatedValue as Float) }
            start()
        }
        if (e) main.postDelayed(autoCollapse, 4000L)
    }

    private fun show(t: String, s: String, prio: Int) {
        if (root == null || landscape) return
        if (expanded && showingPrio > prio) return   // higher priority content stays
        title.text = t; sub.text = s; showingPrio = prio
        setExpanded(true)
    }

    /** Landscape usually means full-screen video or games: hide the island, bring it back in portrait. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientation(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    private fun applyOrientation(land: Boolean) {
        landscape = land
        val r = root ?: return
        if (land) {
            main.removeCallbacks(autoCollapse); anim?.cancel()
            expanded = false; showingPrio = 0
            render(0f)
            r.visibility = View.GONE
        } else {
            render(0f)                 // refit to the portrait screen width
            r.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        running = false; instance = null
        anim?.cancel(); main.removeCallbacksAndMessages(null)
        try { unregisterReceiver(power) } catch (_: Exception) {}
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null

    companion object {
        @Volatile var running = false
        private var instance: OverlayService? = null
        fun post(t: String, s: String, prio: Int) {
            Handler(Looper.getMainLooper()).post { instance?.show(t, s, prio) }
        }
    }
}
