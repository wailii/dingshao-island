package com.xiaodeng.islandprobe

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var statusBox: LinearLayout
    private val resultViews = mutableMapOf<String, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Probe.ensureChannel(this)
        window.setDecorFitsSystemWindows(false)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    // ---------- 页面 ----------

    private fun buildUi(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(col)
        }
        // 状态栏底下垫一条底色，内容滚上去不会压到时间和电量
        val scrim = View(this).apply { setBackgroundColor(BG) }
        val root = FrameLayout(this).apply {
            addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
            setOnApplyWindowInsetsListener { _, ins ->
                val bars = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                col.setPadding(dp(20), bars.top + dp(18), dp(20), bars.bottom + dp(28))
                scrim.layoutParams = scrim.layoutParams.apply { height = bars.top }
                ins
            }
        }

        col.addView(text("岛测试", 30f, INK, bold = true))
        col.addView(text("看这台手机的原子岛，认不认安卓 16 的实况通知", 15f, GREY).also { it.mt(6) })

        col.addView(section("怎么测"))
        col.addView(card().apply {
            addView(step("1", "点一种写法的「发出」。App 会自己退到桌面，1 秒后发出。"))
            addView(step("2", "看屏幕顶上摄像头那一圈有没有亮起胶囊。亮了就点一下、长按一下、下拉通知栏，看它怎么展开。").also { it.mt(12) })
            addView(step("3", "回到这里点下一种。6 种试完，点最下面「复制结果」发给我，亮起来的样子各截一张图。").also { it.mt(12) })
        })

        col.addView(section("本机状态"))
        statusBox = card()
        col.addView(statusBox)

        col.addView(section("6 种写法"))
        Probe.variants.forEachIndexed { i, v ->
            col.addView(variantCard(v).also { if (i > 0) it.mt(10) })
        }

        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(pill("全部撤掉", filled = false) {
                Probe.cancelAll(this@MainActivity)
                toast("已撤掉")
            }, LinearLayout.LayoutParams(0, dp(52), 1f))
            addView(View(context), LinearLayout.LayoutParams(dp(10), 1))
            addView(pill("复制结果", filled = true) { copyReport() }, LinearLayout.LayoutParams(0, dp(52), 1f))
        }.also { it.mt(24) })

        return root
    }

    private fun variantCard(v: Probe.Variant): View = card().apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val left = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(v.name, 17f, INK, bold = true))
            addView(text(v.desc, 14f, GREY).also { it.mt(4) })
            val r = text("", 13f, GREY).also { it.mt(8) }
            resultViews[v.key] = r
            addView(r)
        }
        addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(
            pill("发出", filled = true) { fire(v.key) },
            LinearLayout.LayoutParams(dp(72), dp(40)).apply { leftMargin = dp(14) },
        )
    }

    private fun refresh() {
        statusBox.removeAllViews()
        statusBox.addView(row("机型", "${Build.MANUFACTURER} ${Build.MODEL}"))
        statusBox.addView(row("系统", osName()))
        statusBox.addView(row("安卓", "${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）"))

        val notifOk = notificationsAllowed()
        statusBox.addView(
            row(
                "通知权限", if (notifOk) "已允许" else "没开",
                valueColor = if (notifOk) GOOD else BAD,
                action = if (notifOk) null else ("去开" to { askNotificationPermission() }),
            )
        )

        if (Build.VERSION.SDK_INT >= 36) {
            val can = getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
            statusBox.addView(
                row(
                    "实况通知", if (can) "系统允许" else "系统不允许",
                    valueColor = if (can) GOOD else BAD,
                    action = if (can) null else ("去设置" to { openPromotionSettings() }),
                )
            )
        } else {
            statusBox.addView(row("实况通知", "安卓 16 以下没有", valueColor = BAD))
        }
        statusBox.addView(row("实况开关", promotionOp(), valueColor = GREY))

        for (v in Probe.variants) {
            val tv = resultViews[v.key] ?: continue
            val rec = Probe.recordOf(this, v.key)
            val (msg, color) = describe(rec)
            tv.text = msg
            tv.setTextColor(color)
            if (v.key == "actions") {
                Probe.tappedAction(this)?.let { tv.text = "$msg\n按钮能点：点到了「$it」" }
            }
        }
    }

    private fun describe(rec: Probe.Record?): Pair<String, Int> = when (rec?.result) {
        null -> "还没试" to GREY
        Probe.Result.PROMOTED -> "系统已打上进岛标记" to GOOD
        Probe.Result.PLAIN -> "系统没打进岛标记，只当普通通知" to BAD
        Probe.Result.MISSING -> "通知没发出来" to BAD
    }

    // ---------- 动作 ----------

    private fun fire(key: String) {
        if (!notificationsAllowed()) {
            askNotificationPermission()
            return
        }
        val app = applicationContext
        Probe.cancelAll(app)
        moveTaskToBack(true)
        ui.postDelayed({ Probe.post(app, key) }, 900)
    }

    private fun notificationsAllowed(): Boolean =
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    private fun askNotificationPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }
    }

    private fun openPromotionSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }
    }

    private fun copyReport() {
        val sb = StringBuilder("岛测试结果\n")
        sb.append("机型：${Build.MANUFACTURER} ${Build.MODEL}\n")
        sb.append("系统：${osName()}\n")
        sb.append("版本号：${Build.DISPLAY}\n")
        sb.append("安卓：${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）\n")
        sb.append("通知权限：${if (notificationsAllowed()) "已允许" else "没开"}\n")
        if (Build.VERSION.SDK_INT >= 36) {
            val can = getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
            sb.append("实况通知：${if (can) "系统允许" else "系统不允许"}\n")
        }
        sb.append("实况开关：${promotionOp()}\n")
        for (v in Probe.variants) {
            val rec = Probe.recordOf(this, v.key)
            val promotable = rec?.let { if (it.promotable) "，格式合规" else "，格式不合规" } ?: ""
            sb.append("${v.name}：${describe(rec).first}$promotable\n")
        }
        Probe.tappedAction(this)?.let { sb.append("④ 按钮：点到了「$it」\n") }
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("岛测试结果", sb.toString().trim()))
        toast("已复制，粘贴发给我就行")
    }

    // ---------- 读系统信息 ----------

    private fun osName(): String {
        val vivo = prop("ro.vivo.os.build.display.id")
        if (vivo.isNotEmpty()) {
            val ver = prop("ro.vivo.product.version").ifEmpty { prop("ro.vivo.os.version") }
            return if (ver.isNotEmpty()) "$vivo · $ver" else vivo
        }
        return Build.DISPLAY
    }

    private fun promotionOp(): String {
        val mode = try {
            getSystemService(AppOpsManager::class.java)
                .unsafeCheckOpNoThrow("android:post_promoted_notifications", Process.myUid(), packageName)
        } catch (_: Exception) {
            return "读不到"
        }
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> "允许"
            AppOpsManager.MODE_IGNORED -> "关着"
            AppOpsManager.MODE_DEFAULT -> "默认"
            AppOpsManager.MODE_ERRORED -> "拒绝"
            else -> "其他($mode)"
        }
    }

    private fun prop(key: String): String = try {
        Runtime.getRuntime().exec(arrayOf("getprop", key)).inputStream.bufferedReader().use { it.readText().trim() }
    } catch (_: Exception) {
        ""
    }

    // ---------- 小组件 ----------

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = GradientDrawable().apply {
            setColor(0xFFFFFFFF.toInt())
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), LINE)
        }
    }

    private fun section(label: String): TextView =
        text(label, 13f, GREY, bold = true).apply {
            letterSpacing = 0.04f
            setPadding(dp(4), dp(26), 0, dp(10))
        }

    private fun step(n: String, body: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(TextView(context).apply {
            text = n
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(INK)
            }
        }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { topMargin = dp(1) })
        addView(
            text(body, 15f, INK).apply { setLineSpacing(0f, 1.25f) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12) },
        )
    }

    private fun row(
        label: String,
        value: String,
        valueColor: Int = INK,
        action: Pair<String, () -> Unit>? = null,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(36)
        addView(text(label, 15f, GREY), LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(
            text(value, 15f, valueColor),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        if (action != null) {
            addView(
                pill(action.first, filled = true, small = true) { action.second() },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)),
            )
        }
    }

    private fun pill(label: String, filled: Boolean, small: Boolean = false, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (small) 13f else 15f)
            setTextColor(if (filled) 0xFFFFFFFF.toInt() else INK)
            if (small) setPadding(dp(14), 0, dp(14), 0)
            val shape = GradientDrawable().apply {
                cornerRadius = dp(100).toFloat()
                if (filled) setColor(INK) else {
                    setColor(0xFFFFFFFF.toInt())
                    setStroke(dp(1), 0xFFD9D9D4.toInt())
                }
            }
            background = RippleDrawable(ColorStateList.valueOf(if (filled) 0x33FFFFFF else 0x14000000), shape, null)
            setOnClickListener { onClick() }
        }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun View.mt(dpValue: Int) {
        val lp = layoutParams as? ViewGroup.MarginLayoutParams
            ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(dpValue)
        layoutParams = lp
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val BG = 0xFFF4F4F1.toInt()
        private const val INK = 0xFF111111.toInt()
        private const val GREY = 0xFF85857F.toInt()
        private const val LINE = 0xFFEAEAE5.toInt()
        private const val GOOD = 0xFF1F8F4E.toInt()
        private const val BAD = 0xFFD9480F.toInt()
    }
}
