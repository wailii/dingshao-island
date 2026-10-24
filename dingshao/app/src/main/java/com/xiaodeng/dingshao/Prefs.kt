package com.xiaodeng.dingshao

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

object Prefs {
    private lateinit var sp: SharedPreferences

    private val _task = MutableStateFlow("")
    private val _watched = MutableStateFlow<Set<String>>(emptySet())
    private val _thresholdSec = MutableStateFlow(180)
    private val _monitoring = MutableStateFlow(false)
    private val _monitoringSince = MutableStateFlow(0L)
    private val _onboarded = MutableStateFlow(false)
    private val _expandSec = MutableStateFlow(180)
    private val _closeSec = MutableStateFlow(OFF)
    private val _islandMode = MutableStateFlow(MODE_AUTO)
    private val _dailySec = MutableStateFlow(3600)
    private val _usedToday = MutableStateFlow(0L)
    private val _stats = MutableStateFlow(Stats())

    val task: StateFlow<String> = _task
    val watched: StateFlow<Set<String>> = _watched
    val thresholdSec: StateFlow<Int> = _thresholdSec
    val monitoring: StateFlow<Boolean> = _monitoring
    val monitoringSince: StateFlow<Long> = _monitoringSince
    val onboarded: StateFlow<Boolean> = _onboarded

    /** 岛亮了以后多久自动点开；OFF 不点，0 马上 */
    val expandSec: StateFlow<Int> = _expandSec

    /** 岛亮了以后多久直接关掉 App（退回桌面）；OFF 不关 */
    val closeSec: StateFlow<Int> = _closeSec

    /** 这几个 App 一天一共待多久就喊；OFF 不限 */
    val dailySec: StateFlow<Int> = _dailySec

    /** 今天在盯的 App 里一共待了多少秒（不含正在进行的这一段，那段由 Engine 实时加上） */
    val usedToday: StateFlow<Long> = _usedToday

    /** 提醒用哪种岛：自动（能用系统原子岛就用，不能就自己画）/ 只用系统原子岛 / 只用自己画的 */
    val islandMode: StateFlow<String> = _islandMode
    val stats: StateFlow<Stats> = _stats

    /** 用户还没选过要盯的 App 时为 true，首次载入应用列表后用常见分心 App 填上 */
    val watchedUnset: Boolean get() = !sp.contains(K_WATCHED)

    data class Stats(
        val day: String = today(),
        val caught: Int = 0,
        val returned: Int = 0,
        val snoozed: Int = 0,
        val kicked: Int = 0,
    )

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("dingshao", Context.MODE_PRIVATE)
        _task.value = sp.getString(K_TASK, "") ?: ""
        _watched.value = sp.getStringSet(K_WATCHED, emptySet())?.toSet() ?: emptySet()
        _thresholdSec.value = sp.getInt(K_THRESHOLD, 180)
        _monitoring.value = sp.getBoolean(K_MONITORING, false)
        _monitoringSince.value = sp.getLong(K_SINCE, 0L)
        _onboarded.value = sp.getBoolean(K_ONBOARDED, false)
        _expandSec.value = sp.getInt(K_EXPAND, 180)
        _closeSec.value = sp.getInt(K_CLOSE, OFF)
        _islandMode.value = sp.getString(K_MODE, MODE_AUTO) ?: MODE_AUTO
        _dailySec.value = sp.getInt(K_DAILY, 3600)
        _usedToday.value = if (sp.getString(K_USED_DAY, null) == today()) sp.getLong(K_USED, 0L) else 0L
        _devLog.value = sp.getBoolean("flag_devlog", false)
        val day = sp.getString(K_DAY, null)
        _stats.value = if (day == today()) {
            Stats(day, sp.getInt(K_CAUGHT, 0), sp.getInt(K_RETURNED, 0), sp.getInt(K_SNOOZED, 0), sp.getInt(K_KICKED, 0))
        } else {
            Stats()
        }
    }

    fun setTask(s: String) {
        _task.value = s
        sp.edit().putString(K_TASK, s).apply()
    }

    fun setWatched(set: Set<String>) {
        _watched.value = set
        sp.edit().putStringSet(K_WATCHED, set).apply()
    }

    fun setThreshold(sec: Int) {
        _thresholdSec.value = sec
        sp.edit().putInt(K_THRESHOLD, sec).apply()
    }

    fun setMonitoring(on: Boolean) {
        val since = if (on) System.currentTimeMillis() else 0L
        _monitoring.value = on
        _monitoringSince.value = since
        sp.edit().putBoolean(K_MONITORING, on).putLong(K_SINCE, since).apply()
    }

    fun setExpand(sec: Int) {
        _expandSec.value = sec
        sp.edit().putInt(K_EXPAND, sec).apply()
    }

    fun setClose(sec: Int) {
        _closeSec.value = sec
        sp.edit().putInt(K_CLOSE, sec).apply()
    }

    fun setDaily(sec: Int) {
        _dailySec.value = sec
        sp.edit().putInt(K_DAILY, sec).apply()
    }

    fun addUsed(sec: Long) {
        if (sec <= 0) return
        rollUsed()
        val next = _usedToday.value + sec
        _usedToday.value = next
        sp.edit().putString(K_USED_DAY, today()).putLong(K_USED, next).apply()
    }

    /** 跨天后今天的累计清零 */
    fun rollUsed() {
        if (sp.getString(K_USED_DAY, null) != today() && _usedToday.value != 0L) {
            _usedToday.value = 0L
            sp.edit().putString(K_USED_DAY, today()).putLong(K_USED, 0L).apply()
        }
    }

    fun setIslandMode(m: String) {
        _islandMode.value = m
        sp.edit().putString(K_MODE, m).apply()
    }

    /** 盯梢引擎的现场：进程被系统停掉再拉起来时，从这里接着算 */
    fun saveSession(json: String?) {
        sp.edit().apply { if (json == null) remove(K_SESSION) else putString(K_SESSION, json) }.apply()
    }

    fun loadSession(): String? = sp.getString(K_SESSION, null)

    /** 一些只需要记住「做过没有」的小标记，比如去过 vivo 的后台耗电页 */
    fun flag(key: String): Boolean = sp.getBoolean("flag_$key", false)

    fun setFlag(key: String, on: Boolean = true) {
        // 同步写盘：这些标记少，写完马上退出 App 也不会丢
        sp.edit().putBoolean("flag_$key", on).commit()
        if (key == "devlog") _devLog.value = on
    }

    /** 零碎的字符串、数字：学习那边的状态缓存、岛上答过的题、连着被退了几回 */
    fun str(key: String): String? = sp.getString("s_$key", null)

    fun setStr(key: String, v: String?) {
        sp.edit().apply { if (v == null) remove("s_$key") else putString("s_$key", v) }.apply()
    }

    fun long(key: String): Long = sp.getLong("l_$key", 0L)

    fun setLong(key: String, v: Long) {
        sp.edit().putLong("l_$key", v).apply()
    }

    private val _devLog = MutableStateFlow(false)

    /** 日志入口有没有被连点 10 次打开 */
    val devLog: StateFlow<Boolean> = _devLog

    fun setOnboarded() {
        _onboarded.value = true
        sp.edit().putBoolean(K_ONBOARDED, true).apply()
    }

    fun bump(caught: Int = 0, returned: Int = 0, snoozed: Int = 0, kicked: Int = 0) {
        val cur = _stats.value.let { if (it.day == today()) it else Stats() }
        val next = cur.copy(
            caught = cur.caught + caught,
            returned = cur.returned + returned,
            snoozed = cur.snoozed + snoozed,
            kicked = cur.kicked + kicked,
        )
        _stats.value = next
        sp.edit()
            .putString(K_DAY, next.day)
            .putInt(K_CAUGHT, next.caught)
            .putInt(K_RETURNED, next.returned)
            .putInt(K_SNOOZED, next.snoozed)
            .putInt(K_KICKED, next.kicked)
            .apply()
    }

    /** 跨天后把统计清零 */
    fun rollStats() {
        rollUsed()
        if (_stats.value.day != today()) _stats.value = Stats()
    }

    private fun today() = LocalDate.now().toString()

    const val OFF = -1
    const val MODE_AUTO = "auto"
    const val MODE_SYSTEM = "system"
    const val MODE_OVERLAY = "overlay"

    private const val K_MODE = "island_mode"
    private const val K_DAILY = "daily_sec"
    private const val K_USED = "used_today"
    private const val K_USED_DAY = "used_day"
    private const val K_SESSION = "session"

    private const val K_TASK = "task"
    private const val K_EXPAND = "expand_sec"
    private const val K_CLOSE = "close_sec"
    private const val K_KICKED = "stats_kicked"
    private const val K_WATCHED = "watched"
    private const val K_THRESHOLD = "threshold_sec"
    private const val K_MONITORING = "monitoring"
    private const val K_SINCE = "monitoring_since"
    private const val K_ONBOARDED = "onboarded"
    private const val K_DAY = "stats_day"
    private const val K_CAUGHT = "stats_caught"
    private const val K_RETURNED = "stats_returned"
    private const val K_SNOOZED = "stats_snoozed"
}
