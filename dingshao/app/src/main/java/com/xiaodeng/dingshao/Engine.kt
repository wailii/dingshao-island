package com.xiaodeng.dingshao

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * 盯梢的状态机。无障碍服务告诉它「现在前台是谁」，它决定什么时候点亮、点开原子岛，什么时候把你退回桌面。
 *
 * 一次被抓的时间线（从岛亮起那一刻算）：
 *   每过一个阈值时长升一档，岛跳一下、眼神更凶；
 *   到「自动点开」的时间，岛跳出来后替你点开，之后每次升档都点开；
 *   到「直接关掉」的时间，岛上说一声，然后退回桌面，一分钟内再打开会被再退一次。
 *
 * 装了「学习」时：岛上的按钮换成「去学一节」；「再刷 5 分钟」要先答对一道学习里的判断题；
 * 连着被退回桌面三回还打开，直接送去学习；在学习里学完一节，「每天一共」多 10 分钟。
 */
object Engine {
    private const val GRACE_MS = 12_000L
    private const val COOLDOWN_MS = 60_000L
    private const val TICK_MS = 5_000L
    private const val SNOOZE_MIN = 5
    private const val QUIZ_MS = 30_000L
    private const val WRONG_SHOW_MS = 6_500L

    /** 连着被退回桌面几回以后，再打开就送去学习；隔半小时没再退就重新数 */
    private const val STREAK_TO_STUDY = 3
    private const val STREAK_GAP_MS = 30 * 60_000L

    /** 今天的总时长已经用完时，打开盯的 App 多久后亮岛 */
    private const val OVER_DAILY_MS = 3_000L
    private const val USAGE_FLUSH_MS = 10_000L

    data class Live(
        val trackingPkg: String? = null,
        val trackStart: Long = 0L,
        val islandUp: Boolean = false,
    )

    private val _live = MutableStateFlow(Live())
    val live: StateFlow<Live> = _live

    private val _serviceUp = MutableStateFlow(false)
    val serviceUp: StateFlow<Boolean> = _serviceUp

    private val main = Handler(Looper.getMainLooper())
    private lateinit var app: Context
    private var service: WatchService? = null

    private var foreground: String? = null
    private var trackPkg: String? = null
    private var trackStart = 0L
    private var deadline = 0L
    private var islandUp = false
    private var snoozed = false
    private var snoozedJustNow = false
    private var litAt = 0L
    private var closeAt = 0L
    private var autoExpand = false
    private var expandAt = 0L
    private var lastLevel = 0
    private var cooldownUntil = 0L

    /** 岛亮着时点「去学一节」，学习那边便利贴该说哪句：island 被抓 / wrong 刚答错 */
    private var goFrom = "island"

    /** 岛上正在答的题；null = 没在答 */
    private var quiz: Xuexi.Quiz? = null
    private val quizTimeout = Runnable { safe { quizEnd(null) } }

    private val fireRunnable = Runnable { safe { fire() } }

    /** 在盯的 App 里这一段从什么时候开始算进「今天一共」，0 = 现在不在盯的 App 里 */
    private var segStart = 0L
    private val usageRunnable = object : Runnable {
        override fun run() {
            safe { flushUsage() }
            if (segStart > 0) main.postDelayed(this, USAGE_FLUSH_MS)
        }
    }

    /** 每天一共的额度（秒）：设置的时长 + 今天在学习里学完几节换来的；OFF 不限 */
    fun dailyLimit(): Int = Prefs.dailySec.value.let { if (it > 0) it + Xuexi.bonusSec() else it }

    /** 今天在盯的 App 里一共待了多少秒，含正在进行的这一段 */
    fun todaySec(): Long {
        Prefs.rollUsed()
        val live = if (segStart > 0) (System.currentTimeMillis() - segStart) / 1000 else 0
        return Prefs.usedToday.value + live
    }

    private fun flushUsage() {
        if (segStart <= 0) return
        val now = System.currentTimeMillis()
        val whole = (now - segStart) / 1000
        if (whole > 0) {
            Prefs.addUsed(whole)
            segStart += whole * 1000
        }
    }

    private fun startUsage() {
        if (segStart > 0) return
        segStart = System.currentTimeMillis()
        main.removeCallbacks(usageRunnable)
        main.postDelayed(usageRunnable, USAGE_FLUSH_MS)
    }

    private fun stopUsage() {
        flushUsage()
        segStart = 0L
        main.removeCallbacks(usageRunnable)
    }
    private val graceRunnable = Runnable {
        safe {
            trackPkg?.let { Journal.add(Journal.Kind.App, "这段结束：${label(it)} 共待了 ${Copy.duration(elapsedSec())}") }
            resetTracking()
        }
    }
    /** 进程被拉起来、从磁盘接着算的那一段，10 秒内服务还没连上就作废，免得岛挂在那没人管 */
    private val orphanRunnable = Runnable {
        safe {
            if (service == null && (islandUp || trackPkg != null)) {
                Journal.add(Journal.Kind.Service, "服务没连上，接着算的这一段作废")
                if (islandUp) IslandNotifier.cancel()
                islandUp = false
                resetTracking()
            }
        }
    }
    private val resumeRunnable = Runnable { safe { if (islandUp && quiz == null) resumeCatch() } }
    private val studyCheck = Runnable {
        safe {
            if (foreground in Prefs.watched.value) {
                Journal.add(Journal.Kind.Error, "没能打开学习（系统可能拦了后台弹界面），退回桌面")
                service?.closeShade()
                service?.goHome()
            }
        }
    }
    private val tickRunnable = object : Runnable {
        override fun run() {
            safe { onTick() }
            if (islandUp) main.postDelayed(this, TICK_MS)
        }
    }
    private val expandRunnable = Runnable {
        safe {
            if (islandUp) {
                autoExpand = true
                expandAt = 0L
                lastLevel = Copy.level(elapsedSec(), Prefs.thresholdSec.value)
                bounceAndTap()
            }
        }
    }
    private val tapRunnable = Runnable {
        safe {
            if (islandUp && foreground in Prefs.watched.value) {
                val ok = service?.expandIsland() == true
                Journal.add(Journal.Kind.Island, if (ok) "替你点开了${Rom.islandName}" else "想点开${Rom.islandName}，但没找到它的位置")
            }
        }
    }
    private val closeRunnable = Runnable { safe { kick() } }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Journal.error("盯梢引擎", e)
        }
    }

    private var restored = false

    /** 无障碍服务最近一次断开的时间；连上后清零 */
    @Volatile var lastLostAt = 0L
        private set

    fun attach(ctx: Context) {
        app = ctx.applicationContext
        Prefs.init(app)
        Xuexi.init(app)
        if (!restored) {
            restored = true
            safe { restore() }
        }
    }

    fun bind(s: WatchService?) {
        if ((s != null) != (service != null)) {
            Journal.add(Journal.Kind.Service, if (s != null) "无障碍服务连上了" else "无障碍服务断开了")
        }
        if (s == null && service != null) lastLostAt = System.currentTimeMillis()
        if (s != null) lastLostAt = 0L
        service = s
        _serviceUp.value = s != null
        if (s != null) {
            main.removeCallbacks(orphanRunnable)
        } else if (islandUp || trackPkg != null) {
            // 服务断了就不知道你在哪个 App，岛不能再挂着说瞎话
            safe {
                if (islandUp) IslandNotifier.cancel()
                islandUp = false
                foreground = null
                resetTracking()
            }
        }
    }

    private fun label(pkg: String) = AppCatalog.label(app, pkg)

    fun onForeground(pkg: String) {
        if (pkg == foreground) return
        if (Prefs.monitoring.value) Journal.add(Journal.Kind.Window, "前台：${label(pkg)}（$pkg）")
        // 刚从学习出来：它这会儿还醒着，读一下今天学完几节（广播没收到也能补上庆祝和额度）
        if (foreground == Xuexi.PKG) Xuexi.refresh { safe { onStudyStatus(it) } }
        foreground = pkg
        if (Prefs.monitoring.value) safe { evaluate() }
    }

    fun onScreenOff() {
        stopUsage()
        foreground = null
        if (trackPkg != null) Journal.add(Journal.Kind.App, "锁屏了，这段不算")
        if (islandUp) IslandNotifier.cancel()
        islandUp = false
        resetTracking()
    }

    fun startMonitoring() {
        Journal.add(Journal.Kind.Rule, "开始盯梢")
        Prefs.setMonitoring(true)
        cooldownUntil = 0L
        // 重新开始盯：连着被退的回数重新数
        Prefs.setLong(K_KICK_N, 0L)
        resetTracking()
        evaluate()
    }

    fun stopMonitoring() {
        stopUsage()
        Journal.add(Journal.Kind.Rule, "结束盯梢")
        Prefs.setMonitoring(false)
        if (islandUp) IslandNotifier.cancel()
        islandUp = false
        cooldownUntil = 0L
        resetTracking()
    }

    /** 盯的 App、阈值、自动点开 / 关掉的时间改了，正在计时的那一段按新规则重来 */
    fun onRulesChanged() {
        if (!Prefs.monitoring.value) return
        Journal.add(Journal.Kind.Rule, "改了设置，正在算的这一段重来")
        if (islandUp) IslandNotifier.cancel()
        islandUp = false
        resetTracking()
        evaluate()
    }

    /**
     * 学习那边今天学完的节数涨了：岛上金色笑眼庆祝，「每天一共」多 10 分钟，连着被退的回数清零。
     * 广播和切出学习时的补查都走这里，同一节只庆祝一次；头一回读到学习时只记下底数，之前学的不算。
     */
    fun onStudyStatus(s: Xuexi.Status?, title: String? = null) {
        s ?: return
        if (Prefs.str(K_CEL_DAY) == null) {
            Prefs.setStr(K_CEL_DAY, s.date)
            Prefs.setLong(K_CEL_N, s.done.toLong())
            return
        }
        val seen = if (Prefs.str(K_CEL_DAY) == s.date) Prefs.long(K_CEL_N) else 0L
        if (s.done <= seen) return
        Prefs.setStr(K_CEL_DAY, s.date)
        Prefs.setLong(K_CEL_N, s.done.toLong())
        Prefs.setLong(K_KICK_N, 0L)
        Journal.add(Journal.Kind.App, "今天在学习里学完 ${s.done} 节，每天一共的额度现在是 ${Copy.duration(dailyLimit().toLong())}")
        if (!Prefs.monitoring.value || islandUp) return
        IslandNotifier.showLesson(title ?: s.nextText, s.done, Prefs.dailySec.value > 0)
    }

    fun backToFocus() {
        val home = service?.goHome() == true
        Journal.add(Journal.Kind.Island, if (home) "退回桌面了" else "想退回桌面，但无障碍不在，只能把岛收起来")
        if (home) return
        // 没有无障碍服务时回不了桌面，至少把岛收起来
        if (islandUp) IslandNotifier.cancel()
        islandUp = false
        resetTracking()
    }

    /** 点了「再刷 5 分钟」：学习里有题就先答一道，答对才给；没装学习或还没有题，直接给 */
    fun askSnooze() {
        if (!islandUp || snoozed || quiz != null) return
        val q = if (Xuexi.installed()) Xuexi.pickQuiz() else null
        if (q == null) return snooze()
        quiz = q
        main.removeCallbacks(expandRunnable)
        main.removeCallbacks(tapRunnable)
        main.removeCallbacks(closeRunnable)
        main.removeCallbacks(resumeRunnable)
        Journal.add(Journal.Kind.Island, "要再刷 5 分钟，先答题：${q.q}（答案：${if (q.a) "对" else "不对"}）")
        IslandNotifier.showQuiz(q, System.currentTimeMillis() + QUIZ_MS)
        main.postDelayed(quizTimeout, QUIZ_MS)
        // 题目得展开才看得见：岛重新冒出来以后替你点开
        main.postDelayed(tapRunnable, 1600)
    }

    /** 岛上点了「对」或「不对」 */
    fun answerQuiz(said: Boolean) {
        val q = quiz ?: return
        quizEnd(said == q.a)
    }

    /** ok：答对 / 答错 / null 30 秒没答 */
    private fun quizEnd(ok: Boolean?) {
        val q = quiz ?: return
        quiz = null
        main.removeCallbacks(quizTimeout)
        if (ok != null) Xuexi.answer(q, ok)
        Journal.add(Journal.Kind.Island, when (ok) {
            true -> "答对了，给 5 分钟"
            false -> "答错了，不给刷"
            null -> "30 秒没答，不给刷"
        })
        if (!islandUp) return
        if (ok == true) return snooze(q)
        // 答错、没答：这回不能再赖了；岛上说一声，几秒后回到原来的样子，点开、关掉的时间照旧
        snoozed = true
        goFrom = "wrong"
        if (Xuexi.installed()) Xuexi.markGo(goFrom)
        IslandNotifier.showWrong(q, timeout = ok == null)
        main.removeCallbacks(tapRunnable)
        main.postDelayed(tapRunnable, 1600)
        main.postDelayed(resumeRunnable, WRONG_SHOW_MS)
    }

    private fun resumeCatch() {
        val now = System.currentTimeMillis()
        post(alert = false)
        if (expandAt > 0) main.postDelayed(expandRunnable, (expandAt - now).coerceAtLeast(500))
        if (closeAt > 0) main.postDelayed(closeRunnable, (closeAt - now).coerceAtLeast(1500))
    }

    /** 「再刷 5 分钟」：一次被抓只能用一回，5 分钟后岛重新亮，点开 / 关掉的时间从那时重新算 */
    fun snooze(right: Xuexi.Quiz? = null) {
        if (!islandUp || snoozed) return
        islandUp = false
        snoozed = true
        main.removeCallbacks(tickRunnable)
        main.removeCallbacks(expandRunnable)
        main.removeCallbacks(tapRunnable)
        main.removeCallbacks(closeRunnable)
        main.removeCallbacks(resumeRunnable)
        expandAt = 0L
        if (right != null) IslandNotifier.showRight(right) else IslandNotifier.showSnoozed()
        Prefs.bump(snoozed = 1)
        Journal.add(Journal.Kind.Island, if (right != null) "答对题，再刷 5 分钟" else "你点了「再刷 5 分钟」")
        deadline = System.currentTimeMillis() + SNOOZE_MIN * 60_000L
        main.removeCallbacks(fireRunnable)
        main.postDelayed(fireRunnable, SNOOZE_MIN * 60_000L)
        publish()
    }

    private fun evaluate() {
        val pkg = foreground ?: return
        val now = System.currentTimeMillis()
        if (pkg !in Prefs.watched.value) stopUsage()
        if (pkg in Prefs.watched.value) {
            main.removeCallbacks(graceRunnable)
            if (kickStreak() >= STREAK_TO_STUDY && Xuexi.installed()) {
                toStudy(pkg)
                return
            }
            if (now < cooldownUntil) {
                kickAgain(pkg)
                return
            }
            startUsage()
            if (trackPkg == null) {
                // 单次到点和「今天一共」到点，哪个先到算哪个
                val single = Prefs.thresholdSec.value * 1000L
                val daily = dailyLimit()
                val left = if (daily > 0) (daily - todaySec()) * 1000L else Long.MAX_VALUE
                val wait = if (left < single) left.coerceAtLeast(OVER_DAILY_MS) else single
                Journal.add(
                    Journal.Kind.App,
                    "打开 ${label(pkg)}，开始计时（今天已刷 ${Copy.duration(todaySec())}" +
                        (if (daily > 0) "，每天上限 ${Copy.duration(daily.toLong())}" else "") + "）",
                )
                trackStart = now
                deadline = now + wait
                snoozed = false
                lastLevel = 0
                main.removeCallbacks(fireRunnable)
                main.postDelayed(fireRunnable, deadline - now)
                trackPkg = pkg
            } else {
                val changed = trackPkg != pkg
                trackPkg = pkg
                if (islandUp) {
                    if (changed && quiz == null) post(alert = false)
                } else if (now >= deadline) {
                    fire()
                }
            }
        } else if (trackPkg != null) {
            stopUsage()
            if (islandUp) {
                returned()
            } else {
                Journal.add(Journal.Kind.App, "离开 ${label(trackPkg!!)}（${GRACE_MS / 1000} 秒内回来接着算）")
                main.removeCallbacks(graceRunnable)
                main.postDelayed(graceRunnable, GRACE_MS)
            }
        }
        publish()
    }

    private fun fire() {
        trackPkg ?: return
        if (!Prefs.monitoring.value) return
        // 离开分心 App 还在缓冲期里，等回来再说
        if (foreground !in Prefs.watched.value) return
        val now = System.currentTimeMillis()
        islandUp = true
        snoozedJustNow = snoozed
        litAt = now
        lastLevel = Copy.level(elapsedSec(), Prefs.thresholdSec.value)
        Prefs.bump(caught = 1)
        if (dailyLimit() > 0 && todaySec() >= dailyLimit()) {
            Journal.add(Journal.Kind.Island, "今天一共已刷 ${Copy.duration(todaySec())}，到了每天上限")
        }
        Journal.add(
            Journal.Kind.Island,
            "岛亮了：${label(trackPkg!!)} 已待 ${Copy.duration(elapsedSec())}" +
                "（自动点开 ${Prefs.expandSec.value}s，关掉 ${Prefs.closeSec.value}s，无障碍${if (service != null) "在" else "不在"}）",
        )

        val expand = Prefs.expandSec.value
        val close = Prefs.closeSec.value
        closeAt = if (close >= 0) now + close * 1000L else 0L
        autoExpand = false
        main.removeCallbacks(expandRunnable)
        main.removeCallbacks(closeRunnable)
        main.removeCallbacks(tapRunnable)

        post(alert = true)
        if (Xuexi.installed()) Xuexi.markGo(goFrom)
        if (expand == 0) {
            autoExpand = true
            main.postDelayed(tapRunnable, 1500)
        } else if (expand > 0) {
            expandAt = now + expand * 1000L
            main.postDelayed(expandRunnable, expand * 1000L)
        }
        if (closeAt > 0) main.postDelayed(closeRunnable, closeAt - now)

        main.removeCallbacks(tickRunnable)
        main.postDelayed(tickRunnable, TICK_MS)
        publish()
    }

    /** 每升一档：岛重新冒出来、震一下、换更凶的眼神；开了自动点开的话顺手点开 */
    private fun onTick() {
        trackPkg ?: return
        if (!islandUp) return
        save()
        // 正在答题、刚答错还在说：别跳，免得把题盖掉
        if (Xuexi.installed()) Xuexi.markGo(goFrom)
        if (quiz != null || main.hasCallbacks(resumeRunnable)) return
        val lv = Copy.level(elapsedSec(), Prefs.thresholdSec.value)
        if (lv > lastLevel) {
            lastLevel = lv
            Journal.add(Journal.Kind.Island, "升到第 $lv 档：已待 ${Copy.duration(elapsedSec())}")
            // 马上就到自动点开的时间了，交给它一起跳，免得连跳两次
            if (expandAt > 0 && expandAt - System.currentTimeMillis() <= TICK_MS) return
            snoozedJustNow = false
            if (autoExpand) bounceAndTap() else bounce()
        }
    }

    private fun bounce() {
        IslandNotifier.showCatch(catchState(), alert = true, bounce = true)
    }

    private fun bounceAndTap() {
        bounce()
        main.removeCallbacks(tapRunnable)
        main.postDelayed(tapRunnable, 1600)
    }

    private fun post(alert: Boolean) {
        IslandNotifier.showCatch(catchState(), alert = alert)
    }

    private fun catchState(): IslandNotifier.Catch = IslandNotifier.Catch(
        pkg = trackPkg ?: "",
        trackStart = trackStart,
        thresholdSec = Prefs.thresholdSec.value,
        snoozed = snoozed,
        snoozedJustNow = snoozedJustNow,
        closeAt = closeAt,
        seed = trackStart / 1000,
        dailySec = dailyLimit(),
    )

    /** 到点了：岛上说一声，退回桌面；一分钟冷静期 */
    private fun kick() {
        val pkg = trackPkg ?: return
        if (!islandUp || foreground !in Prefs.watched.value) return
        islandUp = false
        Prefs.bump(kicked = 1)
        noteKick()
        Journal.add(Journal.Kind.Island, "到点了，把 ${label(pkg)} 关掉（退回桌面，连着第 ${kickStreak()} 回）")
        cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
        IslandNotifier.showKicked(pkg)
        resetTracking()
        main.postDelayed({
            safe {
                val ok = service?.goHome() == true
                if (!ok) Journal.add(Journal.Kind.Error, "关掉 ${label(pkg)} 失败：无障碍不在，退不回桌面")
            }
        }, 1300)
    }

    private fun kickAgain(pkg: String) {
        val left = ((cooldownUntil - System.currentTimeMillis()) / 1000).coerceAtLeast(1)
        noteKick()
        Journal.add(Journal.Kind.Island, "冷静期里又打开 ${label(pkg)}，再退一次（连着第 ${kickStreak()} 回）")
        IslandNotifier.showCooldown(pkg, left)
        main.postDelayed({ safe { service?.goHome() } }, 900)
    }

    private fun noteKick() {
        val now = System.currentTimeMillis()
        Prefs.setLong(K_KICK_N, kickStreak() + 1L)
        Prefs.setLong(K_KICK_AT, now)
    }

    /** 连着被退回桌面几回了（上一回离现在超过半小时就算 0） */
    fun kickStreak(): Int =
        if (System.currentTimeMillis() - Prefs.long(K_KICK_AT) > STREAK_GAP_MS) 0 else Prefs.long(K_KICK_N).toInt()

    /**
     * 退了三回还打开：岛上翻个白眼「学会儿习」，送去学习的下一节。
     * 先直接打开；vivo 拦了后台开别的 App，就拉下通知栏、替你点这条提醒上的「去学一节」
     * （全屏的 App 里岛是藏起来的，通知栏总拉得下来）；都不行收起通知栏、退回桌面。
     */
    private fun toStudy(pkg: String) {
        val n = kickStreak()
        stopUsage()
        islandUp = false
        resetTracking()
        Prefs.bump(kicked = 1)
        Prefs.setLong(K_KICK_AT, System.currentTimeMillis())
        Journal.add(Journal.Kind.Island, "已经连着退了 $n 回，又打开 ${label(pkg)}：送去学习")
        IslandNotifier.showToStudy(n)
        Xuexi.markGo("kick")
        main.removeCallbacks(studyCheck)
        val stuck = { foreground in Prefs.watched.value }
        main.postDelayed({ safe { service?.let { Xuexi.open(it, "kick") } } }, 700)
        main.postDelayed({ safe { if (stuck()) service?.openShade() } }, 1700)
        main.postDelayed({ safe { if (stuck() && service?.clickIslandButton("去学一节") != true) service?.closeShade() } }, 2600)
        main.postDelayed(studyCheck, 4500)
    }

    private fun returned() {
        val pkg = trackPkg ?: return
        islandUp = false
        Prefs.bump(returned = 1)
        val study = foreground == Xuexi.PKG
        Journal.add(Journal.Kind.Island, "回来了：这次在 ${label(pkg)} 待了 ${Copy.duration(elapsedSec())}" + if (study) "，去了学习" else "")
        IslandNotifier.showReturned(pkg, elapsedSec(), study)
        resetTracking()
    }

    private fun resetTracking() {
        goFrom = "island"
        quiz = null
        main.removeCallbacks(quizTimeout)
        main.removeCallbacks(resumeRunnable)
        trackPkg = null
        trackStart = 0L
        deadline = 0L
        snoozed = false
        snoozedJustNow = false
        closeAt = 0L
        autoExpand = false
        expandAt = 0L
        main.removeCallbacks(fireRunnable)
        main.removeCallbacks(graceRunnable)
        main.removeCallbacks(tickRunnable)
        main.removeCallbacks(expandRunnable)
        main.removeCallbacks(tapRunnable)
        main.removeCallbacks(closeRunnable)
        publish()
    }

    private const val K_KICK_N = "kick_n"
    private const val K_KICK_AT = "kick_at"
    private const val K_CEL_DAY = "cel_day"
    private const val K_CEL_N = "cel_n"

    private fun elapsedSec(): Long = ((System.currentTimeMillis() - trackStart) / 1000).coerceAtLeast(0)

    private fun publish() {
        _live.value = Live(trackPkg, trackStart, islandUp)
        save()
    }

    private fun save() {
        val now = System.currentTimeMillis()
        if (trackPkg == null && cooldownUntil < now) {
            Prefs.saveSession(null)
            return
        }
        val j = JSONObject()
            .put("savedAt", now)
            .put("trackPkg", trackPkg ?: "")
            .put("trackStart", trackStart)
            .put("deadline", deadline)
            .put("islandUp", islandUp)
            .put("snoozed", snoozed)
            .put("litAt", litAt)
            .put("closeAt", closeAt)
            .put("expandAt", expandAt)
            .put("autoExpand", autoExpand)
            .put("lastLevel", lastLevel)
            .put("cooldownUntil", cooldownUntil)
        Prefs.saveSession(j.toString())
    }

    /** 进程被系统停掉再拉起来：从磁盘接着算，岛重新挂上 */
    private fun restore() {
        val raw = Prefs.loadSession()
        val now = System.currentTimeMillis()
        if (raw == null) {
            // 没有在算的：屏幕上要是还挂着上一个进程留下的岛，收掉
            IslandNotifier.cancel()
            return
        }
        val j = JSONObject(raw)
        if (now - j.optLong("savedAt") > 10 * 60_000L || !Prefs.monitoring.value) {
            Prefs.saveSession(null)
            IslandNotifier.cancel()
            return
        }
        cooldownUntil = j.optLong("cooldownUntil")
        val pkg = j.optString("trackPkg").ifEmpty { null } ?: run { publish(); return }
        trackPkg = pkg
        trackStart = j.optLong("trackStart")
        deadline = j.optLong("deadline")
        islandUp = j.optBoolean("islandUp")
        snoozed = j.optBoolean("snoozed")
        litAt = j.optLong("litAt")
        closeAt = j.optLong("closeAt")
        expandAt = j.optLong("expandAt")
        autoExpand = j.optBoolean("autoExpand")
        lastLevel = j.optInt("lastLevel")
        Journal.add(Journal.Kind.Service, "进程被系统停过，接着算：${label(pkg)} 已待 ${Copy.duration(elapsedSec())}")
        if (islandUp) {
            post(alert = false)
            main.postDelayed(tickRunnable, TICK_MS)
            if (expandAt > 0) main.postDelayed(expandRunnable, (expandAt - now).coerceAtLeast(500))
            if (closeAt > 0) main.postDelayed(closeRunnable, (closeAt - now).coerceAtLeast(1500))
        } else {
            IslandNotifier.cancel()
            main.postDelayed(fireRunnable, (deadline - now).coerceAtLeast(500))
        }
        main.postDelayed(orphanRunnable, 10_000)
        publish()
    }
}
