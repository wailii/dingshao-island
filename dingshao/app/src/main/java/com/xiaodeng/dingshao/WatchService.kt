package com.xiaodeng.dingshao

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import org.json.JSONObject

/** 只看「现在前台是哪个 App」，不读任何屏幕内容 */
class WatchService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    // 服务进程一旦崩掉，系统会把它记成「已崩溃」、不再自动重连，所以这里的活一律兜住
    private val check = Runnable { runCatching { detect() }.onFailure { Journal.error("看前台", it) } }

    /** 不光等系统通知「窗口变了」，盯梢中每 5 秒自己再看一眼，漏掉的切换也能补上 */
    private val poll = object : Runnable {
        override fun run() {
            if (Prefs.monitoring.value) runCatching { detect() }.onFailure { Journal.error("定时看前台", it) }
            handler.postDelayed(this, POLL_MS)
        }
    }
    private var ignore: Set<String> = emptySet()

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            runCatching {
                Journal.add(Journal.Kind.Window, if (i.action == Intent.ACTION_SCREEN_OFF) "锁屏" else "解锁")
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> Engine.onScreenOff()
                    Intent.ACTION_USER_PRESENT -> handler.postDelayed(check, 300)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Journal.init(this)
        Journal.add(Journal.Kind.Service, "无障碍服务 onServiceConnected")
        Engine.attach(this)
        Engine.bind(this)
        ignore = buildIgnore()
        registerReceiver(screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        runCatching { detect() }
        handler.removeCallbacks(poll)
        handler.postDelayed(poll, POLL_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        handler.removeCallbacks(check)
        handler.postDelayed(check, 120)
    }

    override fun onDestroy() {
        Journal.add(Journal.Kind.Service, "无障碍服务 onDestroy")
        Engine.bind(null)
        super.onDestroy()
    }

    override fun onInterrupt() {
        Journal.add(Journal.Kind.Service, "无障碍服务 onInterrupt")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Journal.add(Journal.Kind.Service, "无障碍服务 onUnbind（系统把它停了）")
        handler.removeCallbacks(poll)
        Engine.bind(null)
        try {
            unregisterReceiver(screen)
        } catch (_: IllegalArgumentException) {
        }
        return super.onUnbind(intent)
    }

    fun goHome(): Boolean = runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }.getOrDefault(false)

    /**
     * 替用户点一下原子岛，让它展开成大卡片。
     * 只在确认岛上显示的是我们自己时才点，位置取 vivo 记录的岛区域，不会点到下面的 App。
     */
    fun expandIsland(): Boolean = runCatching { tapIsland() }.getOrDefault(false)

    private fun tapIsland(): Boolean {
        // 已经展开了（系统自己弹开的、你点开的）就别再点，再点就点到卡片上、打开盯梢岛了
        if (islandExpanded()) {
            Journal.add(Journal.Kind.Island, "岛已经是展开的，不用点")
            return true
        }
        val r = islandRegion() ?: return false
        val p = Path().apply { moveTo(r.centerX(), r.centerY()) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 50))
            .build()
        Journal.add(Journal.Kind.Island, "点岛的位置：${r.toShortString()}")
        return dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) {
                Journal.add(Journal.Kind.Island, "点岛的手势做完了")
            }

            override fun onCancelled(d: GestureDescription?) {
                Journal.add(Journal.Kind.Error, "点岛的手势被系统取消了")
            }
        }, null)
    }

    fun openShade(): Boolean = runCatching { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) }.getOrDefault(false)

    fun closeShade(): Boolean = runCatching { performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE) }.getOrDefault(false)

    /**
     * 在系统界面（通知栏、展开的岛）里找写着 label 的按钮点一下。系统当成你自己点的，按钮能打开别的 App；
     * 盯梢岛自己从后台直接开别的 App，vivo 会悄悄拦掉。只找系统窗口，不会点到下面 App 里同名的字。
     */
    fun clickIslandButton(label: String): Boolean = runCatching {
        val seen = StringBuilder()
        for (w in windows.orEmpty()) {
            seen.append("${w.type}/${w.title} ")
            if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = w.root ?: continue
            val hits = root.findAccessibilityNodeInfosByText(label).ifEmpty { listOfNotNull(findText(root, label, 0)) }
            for (n in hits) {
                val r = Rect().also { n.getBoundsInScreen(it) }
                if (r.isEmpty) continue
                // 按位置点一下，系统按手指点的算
                val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
                dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build(), null, null)
                Journal.add(Journal.Kind.Island, "替你点了「$label」${r.toShortString()}（${w.title}）")
                return@runCatching true
            }
        }
        Journal.add(Journal.Kind.Island, "没找到「$label」，当时的窗口：$seen")
        false
    }.getOrElse {
        Journal.error("点系统界面上的按钮", it)
        false
    }

    /** findAccessibilityNodeInfosByText 在有的系统界面里找不到，自己往下翻一遍 */
    private fun findText(n: AccessibilityNodeInfo, label: String, depth: Int): AccessibilityNodeInfo? {
        if (depth > 30) return null
        if (n.text?.toString()?.contains(label) == true || n.contentDescription?.toString()?.contains(label) == true) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            findText(c, label, depth + 1)?.let { return it }
        }
        return null
    }

    /** vivo 的岛状态里 state 1 是收着、2 是展开 */
    private fun islandExpanded(): Boolean = runCatching {
        val j = JSONObject(Settings.Secure.getString(contentResolver, "island_state_json") ?: return false)
        j.optString("sourcePackageName") == packageName && j.optInt("state") == 2
    }.getOrDefault(false)

    private fun islandRegion(): RectF? {
        // vivo 把当前岛的归属和位置写在这里：{"sourcePackageName":..,"regionLeft":..,..}
        try {
            val raw = Settings.Secure.getString(contentResolver, "island_state_json")
            if (!raw.isNullOrEmpty()) {
                val j = JSONObject(raw)
                if (j.optString("sourcePackageName") == packageName) {
                    val r = RectF(
                        j.optInt("regionLeft").toFloat(), j.optInt("regionTop").toFloat(),
                        j.optInt("regionRight").toFloat(), j.optInt("regionBottom").toFloat(),
                    )
                    if (r.width() > 40 && r.height() > 20) return r
                }
                return null
            }
        } catch (_: Exception) {
        }
        // 读不到时退而求其次：找顶部正中那块系统窗口
        val w = resources.displayMetrics.widthPixels
        return windows.orEmpty()
            .filter { it.type == AccessibilityWindowInfo.TYPE_SYSTEM }
            .map { Rect().also { r -> it.getBoundsInScreen(r) } }
            .firstOrNull { it.top <= 8 && it.height() in 48..260 && it.width() in 160..(w * 0.8).toInt() && kotlin.math.abs(it.centerX() - w / 2) < w / 8 }
            ?.let { RectF(it) }
    }

    private fun detect() {
        val pkg = topApp() ?: return
        if (pkg in ignore) return
        Engine.onForeground(pkg)
    }

    /**
     * 现在「主要在用」的是哪个 App。通知栏、输入法、原子岛这些系统窗口不算。
     * 开着小窗时按占满屏幕的那个算：在抖音里开个微信小窗回消息，还算你在刷抖音。
     * 分屏时两个窗口都大，按拿着焦点的那个算。
     */
    private fun topApp(): String? {
        val ws = try {
            windows
        } catch (_: Exception) {
            null
        }
        if (!ws.isNullOrEmpty()) {
            val dm = resources.displayMetrics
            val screen = dm.widthPixels.toLong() * dm.heightPixels
            val apps = ws.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .map { w -> w to Rect().also { w.getBoundsInScreen(it) } }
            val big = apps.filter { (_, r) -> r.width().toLong() * r.height() >= screen * 0.4 }
            val w = big.firstOrNull { it.first.isFocused }?.first
                ?: big.maxByOrNull { (_, r) -> r.width().toLong() * r.height() }?.first
                ?: apps.firstOrNull { it.first.isFocused }?.first
                ?: apps.firstOrNull { it.first.isActive }?.first
                ?: apps.firstOrNull()?.first
            val pkg = w?.root?.packageName?.toString()
            // 有不止一个应用窗口（小窗、分屏、画中画）时，把每个窗口记一笔，变了才记
            val visible = apps.filter { (_, r) -> r.width() >= dm.widthPixels / 4 && r.height() >= dm.heightPixels / 6 }
            if (visible.size > 1 && Prefs.monitoring.value) {
                val desc = visible.joinToString("；") { (win, r) ->
                    val p = runCatching { win.root?.packageName?.toString() }.getOrNull() ?: "?"
                    "${AppCatalog.label(this, p)} ${r.width()}×${r.height()}${if (win.isFocused) " 焦点" else ""}${if (win.isInPictureInPictureMode) " 画中画" else ""}"
                } + " → 算 ${pkg?.let { AppCatalog.label(this, it) }}"
                if (desc != lastWindows) {
                    lastWindows = desc
                    Journal.add(Journal.Kind.Window, "多个窗口：$desc")
                }
            } else {
                lastWindows = ""
            }
            pkg?.let { return it }
        }
        return rootInActiveWindow?.packageName?.toString()
    }

    private var lastWindows = ""

    private companion object {
        const val POLL_MS = 5_000L
    }

    private fun buildIgnore(): Set<String> {
        val s = mutableSetOf("com.android.systemui", "android")
        try {
            getSystemService(InputMethodManager::class.java)
                .enabledInputMethodList
                .forEach { s += it.packageName }
        } catch (_: Exception) {
        }
        return s
    }
}
