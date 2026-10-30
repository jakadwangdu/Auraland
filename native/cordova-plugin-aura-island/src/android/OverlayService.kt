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
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import kotlin.math.roundToInt

internal class IslandRoot(c: Context) : FrameLayout(c) {
    var onOutside: (() -> Unit)? = null
    var onBack: (() -> Unit)? = null

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_OUTSIDE) { onOutside?.invoke(); return true }
        return super.onTouchEvent(e)
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) { onBack?.invoke(); return true }
        return super.dispatchKeyEvent(e)
    }
}

/** The island: a native overlay window with idle, notification, quick-menu and AI modes. */
class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private var root: IslandRoot? = null
    private lateinit var bg: GradientDrawable
    private lateinit var noti: LinearLayout
    private lateinit var menu: LinearLayout
    private lateinit var aiPanel: LinearLayout
    private lateinit var title: TextView
    private lateinit var sub: TextView
    private lateinit var reply: TextView
    private lateinit var input: EditText
    private var torchChip: TextView? = null
    private val main = Handler(Looper.getMainLooper())
    private var anim: ValueAnimator? = null
    private var mode = IDLE
    private var curW = 0f
    private var curH = 0f
    private var baseX = 0
    private var baseY = 0
    private var menuRows = 2
    private var showingPrio = 0
    private var landscape = false
    private val autoCollapse = Runnable { if (mode == NOTIF || mode == MENU) setMode(IDLE) }

    private val power = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_POWER_CONNECTED) {
                val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val pct = if (b == null) 0 else b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / b.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                show("Charging", "$pct%", 1)
            }
        }
    }

    private fun dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

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
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("aura", "Island status", NotificationManager.IMPORTANCE_MIN))
        }
        val n = Notification.Builder(this, "aura").setContentTitle("Aura Island is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    private fun tv(sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.setTextColor(color)
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    private fun styleChip(t: TextView, active: Boolean) {
        t.setTextColor(if (active) Color.BLACK else Color.WHITE)
        val d = GradientDrawable()
        d.cornerRadius = dp(18f)
        d.setColor(if (active) Color.WHITE else Color.parseColor("#1A1A1A"))
        t.background = d
    }

    private fun chip(label: String, click: () -> Unit): TextView {
        val t = tv(12f, Color.WHITE)
        t.text = label
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        val pad = dp(6f).toInt()
        t.setPadding(pad, 0, pad, 0)
        styleChip(t, false)
        val lpc = LinearLayout.LayoutParams(0, dp(36f).toInt(), 1f)
        val m = dp(3f).toInt()
        lpc.setMargins(m, m, m, m)
        t.layoutParams = lpc
        t.setOnClickListener { click() }
        return t
    }

    private fun buildView() {
        val stroke = dp(1f).toInt()
        bg = GradientDrawable()
        bg.setColor(Color.BLACK)
        bg.setStroke(stroke, Color.parseColor("#26FFFFFF"))

        title = tv(14f, Color.WHITE, true)
        sub = tv(12f, Color.parseColor("#A3A3A3"))
        sub.maxLines = 2
        sub.ellipsize = TextUtils.TruncateAt.END
        noti = LinearLayout(this)
        noti.orientation = LinearLayout.VERTICAL
        noti.gravity = Gravity.CENTER_VERTICAL
        noti.setPadding(dp(20f).toInt(), dp(8f).toInt(), dp(20f).toInt(), dp(8f).toInt())
        noti.addView(title)
        noti.addView(sub)
        noti.visibility = View.GONE

        menu = LinearLayout(this)
        menu.orientation = LinearLayout.VERTICAL
        menu.setPadding(dp(10f).toInt(), dp(8f).toInt(), dp(10f).toInt(), dp(8f).toInt())
        menu.visibility = View.GONE

        reply = tv(13f, Color.parseColor("#E6E6E6"))
        reply.maxLines = 6
        reply.ellipsize = TextUtils.TruncateAt.END
        input = EditText(this)
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.parseColor("#7D7D7D"))
        input.hint = "Ask Aura"
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        input.background = null
        input.maxLines = 1
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { send(); true } else false
        }
        val sendChip = chip("Send") { send() }
        sendChip.layoutParams = LinearLayout.LayoutParams(dp(64f).toInt(), dp(36f).toInt())
        val inputRow = LinearLayout(this)
        inputRow.orientation = LinearLayout.HORIZONTAL
        inputRow.gravity = Gravity.CENTER_VERTICAL
        inputRow.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        inputRow.addView(sendChip)
        aiPanel = LinearLayout(this)
        aiPanel.orientation = LinearLayout.VERTICAL
        aiPanel.setPadding(dp(18f).toInt(), dp(14f).toInt(), dp(14f).toInt(), dp(10f).toInt())
        aiPanel.addView(reply, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        aiPanel.addView(inputRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        aiPanel.visibility = View.GONE

        val r = IslandRoot(this)
        r.background = bg
        r.clipToOutline = true
        r.outlineProvider = ViewOutlineProvider.BACKGROUND
        val fill = ViewGroup.LayoutParams.MATCH_PARENT
        r.addView(noti, FrameLayout.LayoutParams(fill, fill))
        r.addView(menu, FrameLayout.LayoutParams(fill, fill))
        r.addView(aiPanel, FrameLayout.LayoutParams(fill, fill))
        r.setOnClickListener {
            if (mode == IDLE) setMode(MENU) else if (mode == NOTIF) setMode(IDLE)
        }
        r.setOnLongClickListener {
            if (mode == IDLE) { openAi(); true } else false
        }
        r.onOutside = { if (mode != IDLE) setMode(IDLE) }
        r.onBack = { if (mode != IDLE) setMode(IDLE) }

        curW = dp(130f)
        curH = dp(34f)
        loadPos()
        lp = WindowManager.LayoutParams(
            curW.toInt(), curH.toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        lp.x = baseX
        lp.y = baseY
        bg.cornerRadius = curH / 2f
        wm.addView(r, lp)
        root = r
    }

    // ---- position -------------------------------------------------------

    private fun loadPos() {
        val p = getSharedPreferences("aura", Context.MODE_PRIVATE)
        baseX = dp(p.getInt("pos_x", 0).toFloat()).toInt()
        baseY = dp(p.getInt("pos_y", 8).toFloat()).toInt()
    }

    private fun repos() {
        loadPos()
        layout()
    }

    /** Centres the idle pill on the camera cutout. Returns null on success, or a message. */
    private fun doAlign(): String? {
        val r = root ?: return "Start the island first"
        if (Build.VERSION.SDK_INT < 28) return "Auto-align needs Android 9 or newer. Use the sliders."
        val cut = r.rootWindowInsets?.displayCutout ?: return "No camera cutout found. Use the sliders."
        val rect = cut.boundingRects.firstOrNull { it.top <= 2 } ?: return "No top camera cutout found. Use the sliders."
        val d = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        val x = ((rect.exactCenterX() - screenW / 2f) / d).roundToInt()
        val y = ((rect.exactCenterY() - dp(34f) / 2f) / d).roundToInt().coerceAtLeast(0)
        getSharedPreferences("aura", Context.MODE_PRIVATE).edit().putInt("pos_x", x).putInt("pos_y", y).apply()
        repos()
        return null
    }

    // ---- modes ----------------------------------------------------------

    private fun sizeFor(m: Int): Pair<Float, Float> {
        val maxW = minOf(dp(340f), resources.displayMetrics.widthPixels - dp(16f))
        return when (m) {
            NOTIF -> Pair(maxW, dp(84f))
            MENU -> Pair(maxW, dp(16f + 42f * menuRows))
            AI -> Pair(maxW, dp(190f))
            else -> Pair(dp(130f), dp(34f))
        }
    }

    private fun layout() {
        val r = root ?: return
        lp.width = curW.toInt()
        lp.height = curH.toInt()
        lp.x = baseX
        lp.y = baseY
        bg.cornerRadius = minOf(curH / 2f, dp(28f))
        r.invalidateOutline()
        try { wm.updateViewLayout(r, lp) } catch (e: Exception) { }
    }

    private fun setFocusable(on: Boolean) {
        val f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        lp.flags = if (on) (lp.flags and f.inv()) else (lp.flags or f)
    }

    private fun hideKeyboard() {
        val r = root ?: return
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(r.windowToken, 0)
        input.clearFocus()
    }

    private fun setMode(m: Int) {
        if (root == null) return
        mode = m
        main.removeCallbacks(autoCollapse)
        if (m == MENU) buildMenu()
        val target: View? = when (m) {
            NOTIF -> noti
            MENU -> menu
            AI -> aiPanel
            else -> null
        }
        for (v in listOf<View>(noti, menu, aiPanel)) if (v !== target) v.visibility = View.GONE
        if (target != null) { target.alpha = 0f; target.visibility = View.VISIBLE }
        setFocusable(m == AI)
        if (m != AI) hideKeyboard()
        val size = sizeFor(m)
        val w = size.first
        val h = size.second
        val sw = curW
        val sh = curH
        anim?.cancel()
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = if (m == IDLE) 220L else 320L
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            val p = it.animatedValue as Float
            curW = sw + (w - sw) * p
            curH = sh + (h - sh) * p
            layout()
            if (target != null) target.alpha = p
        }
        anim = a
        a.start()
        if (m == NOTIF) main.postDelayed(autoCollapse, 4000L)
        if (m == MENU) main.postDelayed(autoCollapse, 10000L)
        if (m == IDLE) showingPrio = 0
    }

    private fun shortcuts(): List<Pair<String, String>> {
        return try {
            val a = JSONArray(getSharedPreferences("aura", Context.MODE_PRIVATE).getString("shortcuts", "[]"))
            (0 until minOf(a.length(), 4)).map { i ->
                val o = a.getJSONObject(i)
                Pair(o.optString("label"), o.optString("pkg"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun row(vararg v: View) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        for (x in v) r.addView(x)
        menu.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun buildMenu() {
        menu.removeAllViews()
        val tc = chip("Torch") {
            val on = Quick.torch(this, "toggle") == "Torch on"
            torchChip?.let { styleChip(it, on) }
        }
        styleChip(tc, Quick.torchIsOn())
        torchChip = tc
        row(
            tc,
            chip("Scan") { Quick.scanner(this); setMode(IDLE) },
            chip("Camera") { Quick.camera(this); setMode(IDLE) },
            chip("Calc") { Quick.calculator(this); setMode(IDLE) }
        )
        val sc = shortcuts()
        menuRows = 2
        if (sc.isNotEmpty()) {
            menuRows = 3
            val chips = sc.map { s -> chip(s.first) { Quick.launchPkg(this, s.second); setMode(IDLE) } }
            row(*chips.toTypedArray())
        }
        row(chip("Ask AI") { openAi() })
    }

    private fun openAi() {
        reply.text = "Ask me anything, or try: torch on, open YouTube, timer 5 min."
        input.setText("")
        setMode(AI)
        input.requestFocus()
        main.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 400L)
    }

    private fun send() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        reply.text = "..."
        Thread {
            val r = try { AiEngine.handle(this, t) } catch (e: Exception) { "Something went wrong." }
            main.post { reply.text = r }
        }.start()
    }

    private fun show(t: String, s: String, prio: Int) {
        if (root == null || landscape || mode == AI || mode == MENU) return
        if (mode == NOTIF && showingPrio > prio) return
        title.text = t
        sub.text = s
        showingPrio = prio
        setMode(NOTIF)
    }

    /** Landscape usually means full-screen video or games: hide the island, bring it back in portrait. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientation(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    private fun applyOrientation(land: Boolean) {
        landscape = land
        val r = root ?: return
        val size = sizeFor(IDLE)
        if (land) {
            main.removeCallbacks(autoCollapse)
            anim?.cancel()
            hideKeyboard()
            mode = IDLE
            showingPrio = 0
            setFocusable(false)
            noti.visibility = View.GONE
            menu.visibility = View.GONE
            aiPanel.visibility = View.GONE
            curW = size.first
            curH = size.second
            r.visibility = View.GONE
        } else {
            r.visibility = View.VISIBLE
            curW = size.first
            curH = size.second
            layout()
        }
    }

    override fun onDestroy() {
        running = false
        instance = null
        anim?.cancel()
        main.removeCallbacksAndMessages(null)
        try { unregisterReceiver(power) } catch (e: Exception) { }
        root?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        root = null
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null

    companion object {
        private const val IDLE = 0
        private const val NOTIF = 1
        private const val MENU = 2
        private const val AI = 3

        @Volatile var running = false
        private var instance: OverlayService? = null

        fun post(t: String, s: String, prio: Int) {
            Handler(Looper.getMainLooper()).post { instance?.show(t, s, prio) }
        }

        fun reloadPos() {
            Handler(Looper.getMainLooper()).post { instance?.repos() }
        }

        fun autoAlign(cb: (String?) -> Unit) {
            Handler(Looper.getMainLooper()).post { cb(instance?.doAlign() ?: "Start the island first") }
        }
    }
}
