package com.xiaodeng.dingshao

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * 原子岛就是一条安卓 16 的实况通知（promoted ongoing）。
 * 平时不发任何通知；只有到阈值才发，离开分心 App 就撤。
 *
 * 岛的外形和展开动画归系统；岛里的眼睛是 30 帧/秒的动图，系统自己循环播放：
 * 刚被抓到时左右扫、眨眼（白），越待越久越不耐烦（琥珀色半眯眼），超时太多就生气（红色倒八字眼）。
 */
object IslandNotifier {
    private const val CHANNEL = "catch_v1"
    private const val CHANNEL_ANGRY = "angry_v1"
    private const val ID = 42
    private const val EXTRA_REQUEST_PROMOTED = "android.requestPromotedOngoing"

    /** 每秒看一次要不要重发；只有标题、眼神、线头变了才马上发，导火索每走 2% 才发、两次至少隔 3 秒 */
    private const val PROGRESS_MS = 1_000L
    private const val MIN_GAP_MS = 3_000L
    private const val FUSE_STEP = 20

    /**
     * 自动过期：每次重发都续上。盯梢岛要是被系统停掉、没人更新了，岛 25 秒后自己消失，
     * 不会挂着一个停在「1 分钟」的旧状态。
     */
    private const val WATCHDOG_MS = 25_000L
    private const val KEEPALIVE_MS = 15_000L

    /** 三档眼神：OriginOS 7 上用动图（30 帧/秒，白 → 琥珀 → 红），别家先用单色静态图标 */
    private val LIVE: IntArray
        get() = if (Rom.animatedIcons) {
            intArrayOf(R.drawable.eyes_calm_live, R.drawable.eyes_annoyed_live, R.drawable.eyes_angry_live)
        } else {
            intArrayOf(R.drawable.ic_eyes, R.drawable.ic_eyes_annoyed, R.drawable.ic_eyes_angry)
        }

    /** 骑在导火索上的眼睛一律白色，压在琥珀、红色的线上也看得清 */
    private val RIDER: IntArray
        get() = if (Rom.animatedIcons) {
            intArrayOf(R.drawable.eyes_calm_live, R.drawable.eyes_annoyed_white_live, R.drawable.eyes_angry_white_live)
        } else {
            intArrayOf(R.drawable.ic_eyes, R.drawable.ic_eyes_annoyed, R.drawable.ic_eyes_angry)
        }

    private val HAPPY: Int get() = if (Rom.animatedIcons) R.drawable.eyes_happy_live else R.drawable.ic_eyes_happy
    private val ANNOYED_ICON: Int get() = if (Rom.animatedIcons) R.drawable.eyes_annoyed_live else R.drawable.ic_eyes_annoyed
    private val ANGRY_ICON: Int get() = if (Rom.animatedIcons) R.drawable.eyes_angry_live else R.drawable.ic_eyes_angry
    private val FACE: Int get() = if (Rom.animatedIcons) R.drawable.face_angry_live else R.drawable.face_angry

    /** 出题：蓝色，一只眼眯着打量你；学完一节、答对：金色笑眼带星星；退了三回还来：琥珀色翻白眼 */
    private val QUIZ: Int get() = if (Rom.animatedIcons) R.drawable.eyes_quiz_live else R.drawable.ic_eyes
    private val PROUD: Int get() = if (Rom.animatedIcons) R.drawable.eyes_proud_live else R.drawable.ic_eyes_happy
    private val ROLL: Int get() = if (Rom.animatedIcons) R.drawable.eyes_roll_live else R.drawable.ic_eyes_annoyed
    private const val QUIZ_COLOR = 0xFF7EC8FF.toInt()

    /** 导火索的颜色跟眼神走 */
    private val FUSE = intArrayOf(0xFFF2F1EC.toInt(), 0xFFFFB547.toInt(), 0xFFFF3B2B.toInt())

    data class Catch(
        val pkg: String,
        val trackStart: Long,
        val thresholdSec: Int,
        val snoozed: Boolean,
        val snoozedJustNow: Boolean,
        val closeAt: Long,
        val seed: Long,
        /** 每天上限（秒），OFF 不限 */
        val dailySec: Int = Prefs.OFF,
    )

    private val handler = Handler(Looper.getMainLooper())
    private val cancelRunnable = Runnable { cancel() }
    private val progressRunnable = object : Runnable {
        override fun run() {
            runCatching { postCatch(alert = false) }
            handler.postDelayed(this, PROGRESS_MS)
        }
    }

    /** 答题的倒计时条：每 3 秒往回烧一截 */
    private var quizOn: Pair<Xuexi.Quiz, Long>? = null
    private val quizRunnable = object : Runnable {
        override fun run() {
            val (q, end) = quizOn ?: return
            if (System.currentTimeMillis() >= end) return
            runCatching { postQuiz(q, end, alert = false) }
            handler.postDelayed(this, MIN_GAP_MS)
        }
    }
    private lateinit var appCtx: Context
    private var catch: Catch? = null
    private var lastKey = ""
    private var lastMajor = ""
    private var lastPostAt = 0L
    private var verified = false

    fun ensureChannel(ctx: Context) {
        appCtx = ctx.applicationContext
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null && nm.getNotificationChannel(CHANNEL_ANGRY) != null) return
        val ch = NotificationChannel(CHANNEL, "到点提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "在分心 App 里待太久时，原子岛亮起来提醒你"
            setSound(null, null as AudioAttributes?)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 30, 70, 30)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
        val angry = NotificationChannel(CHANNEL_ANGRY, "超时太久", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "超过阈值三倍以后，岛每跳一次震得更凶"
            setSound(null, null as AudioAttributes?)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 90, 60, 90, 60, 220)
            setShowBadge(false)
        }
        nm.createNotificationChannel(angry)
    }

    /**
     * 被抓到：胶囊里是眨眼的眼睛 + 系统秒表（从进入分心 App 算起），
     * 展开后是文案、导火索和按钮。
     * bounce = 先撤再发，系统会把岛从摄像头里重新「冒」出来一次。
     */
    fun showCatch(c: Catch, alert: Boolean, bounce: Boolean = false, preview: Boolean = false) {
        handler.removeCallbacks(cancelRunnable)
        handler.removeCallbacks(progressRunnable)
        if (catch == null) verified = false
        catch = c
        lastKey = ""
        lastMajor = ""
        if (bounce) {
            handler.removeCallbacks(verifyRunnable)
            lastState = ""
            nm().cancel(ID)
            handler.postDelayed({
                runCatching { postCatch(alert = true) }
                handler.postDelayed(progressRunnable, PROGRESS_MS)
            }, 420)
        } else {
            runCatching { postCatch(alert) }
            handler.postDelayed(progressRunnable, PROGRESS_MS)
        }
        if (preview) handler.postDelayed(cancelRunnable, 15_000)
    }

    /** 离开分心 App：绿色笑眼 +「回来了」，两秒半后收起 */
    fun showReturned(pkg: String, elapsedSec: Long, study: Boolean = false) {
        stopProgress()
        val app = AppCatalog.label(appCtx, pkg)
        val b = base(if (study) PROUD else HAPPY)
            .setContentTitle(if (study) "去学习了，好样的" else Copy.returnedTitle())
            .setContentText(Copy.returnedText(app, elapsedSec, if (study) null else task()))
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
        if (Build.VERSION.SDK_INT >= 36) b.setShortCriticalText(if (study) "学习去" else "回来了")
        nm().notifySafe(b.build())
        handler.postDelayed(cancelRunnable, 2600)
    }

    /** 点了「再刷 5 分钟」：斜眼看你一下，「5 分钟后见」，然后收起 */
    fun showSnoozed() {
        stopProgress()
        val b = base(ANNOYED_ICON)
            .setContentTitle("行，5 分钟后再来喊你")
            .setContentText("只能赖这一次")
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
        if (Build.VERSION.SDK_INT >= 36) b.setShortCriticalText("5分钟后见")
        nm().notifySafe(b.build())
        handler.postDelayed(cancelRunnable, 2200)
    }

    /** 到「直接关掉」的时间：红眼 +「帮你关了」，随后退回桌面 */
    fun showKicked(pkg: String) {
        stopProgress()
        val app = AppCatalog.label(appCtx, pkg)
        val b = base(ANGRY_ICON, CHANNEL_ANGRY)
            .setContentTitle(Copy.kickedTitle(app))
            .setContentText(Copy.kickedText(task()))
            .setLargeIcon(Icon.createWithResource(appCtx, FACE))
            .setShowWhen(false)
            .setOnlyAlertOnce(false)
        if (Build.VERSION.SDK_INT >= 36) b.setShortCriticalText("关了")
        nm().notifySafe(b.build())
        handler.postDelayed(cancelRunnable, 3000)
    }

    /** 冷静期里又打开：再退一次 */
    fun showCooldown(pkg: String, leftSec: Long) {
        stopProgress()
        val app = AppCatalog.label(appCtx, pkg)
        val b = base(ANGRY_ICON, CHANNEL_ANGRY)
            .setContentTitle(Copy.cooldownTitle(app))
            .setContentText("还剩 ${leftSec} 秒")
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
        if (Build.VERSION.SDK_INT >= 36) b.setShortCriticalText("${leftSec}秒")
        nm().notifySafe(b.build())
        handler.postDelayed(cancelRunnable, 2200)
    }

    /** 「再刷 5 分钟」前先答题：蓝色打量眼，题目当标题，倒计时 30 秒，按钮「对」「不对」 */
    fun showQuiz(q: Xuexi.Quiz, end: Long) {
        stopProgress()
        quizOn = q to end
        // 先撤再发：岛从摄像头里重新冒出来，Engine 随后替你点开
        nm().cancel(ID)
        handler.postDelayed({
            if (quizOn?.first == q) {
                runCatching { postQuiz(q, end, alert = true) }
                handler.postDelayed(quizRunnable, MIN_GAP_MS)
            }
        }, 420)
    }

    private fun postQuiz(q: Xuexi.Quiz, end: Long, alert: Boolean) {
        val now = System.currentTimeMillis()
        val total = 30_000L
        val p = (((now - (end - total)).toDouble() / total) * 1000).toInt().coerceIn(0, 1000)
        lastPostAt = now
        val b = base(QUIZ)
            .setContentTitle(q.q)
            .setContentText("${q.cat.ifEmpty { "学习" }} · 对还是不对？答对再刷 5 分钟")
            .setWhen(end)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOnlyAlertOnce(!alert)
            .addAction(action(ActionReceiver.ACTION_TRUE, "对", 3))
            .addAction(action(ActionReceiver.ACTION_FALSE, "不对", 4))
        if (!Rom.chipRunsTimer && Build.VERSION.SDK_INT >= 36) b.setShortCriticalText("答题")
        if (Build.VERSION.SDK_INT >= 36) {
            b.setStyle(
                Notification.ProgressStyle()
                    .setStyledByProgress(true)
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(1000).setColor(QUIZ_COLOR)))
                    .setProgress(p)
                    .setProgressTrackerIcon(Icon.createWithResource(appCtx, RIDER[0]))
                    .setProgressEndIcon(Icon.createWithResource(appCtx, R.drawable.close_mark))
            )
        }
        nm().notifySafe(b.build())
    }

    /** 答对了：金色笑眼，「答对了！再刷 5 分钟」，说一句为什么，然后收起 */
    fun showRight(q: Xuexi.Quiz) {
        stopProgress()
        val b = base(PROUD)
            .setContentTitle("答对了！再刷 5 分钟")
            .setContentText(q.why.ifEmpty { "5 分钟后见" })
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
        if (Build.VERSION.SDK_INT >= 36) b.setShortCriticalText("答对了")
        nm().notifySafe(b.build())
        handler.postDelayed(cancelRunnable, 2800)
    }

    /** 答错、没答：红眼，说对的是什么、为什么；按钮「去学一节」「回去专注」。几秒后 Engine 把岛换回原来的样子 */
    fun showWrong(q: Xuexi.Quiz, timeout: Boolean) {
        stopProgress()
        val right = if (q.a) "对" else "不对"
        val b = base(ANGRY_ICON, CHANNEL_ANGRY)
            .setContentTitle(if (timeout) "没答，不给刷" else "答错了，应该是「$right」")
            .setContentText(if (timeout) "${q.q}：$right" else q.why.ifEmpty { q.q })
            .setLargeIcon(Icon.createWithResource(appCtx, FACE))
            .setShowWhen(false)
            .setOnlyAlertOnce(false)
            .addAction(studyAction("wrong"))
            .addAction(action(ActionReceiver.ACTION_BACK, "回去专注", 1))
        if (Build.VERSION.SDK_INT >= 36) b.setShortCriticalText(if (timeout) "没答" else "答错了")
        nm().cancel(ID)
        val n = b.build()
        handler.postDelayed({ nm().notifySafe(n) }, 420)
    }

    /** 连着退了几回还打开：琥珀色翻白眼，「学会儿习」，随后送去学习；按钮留着，系统拦了自动打开时还能自己点 */
    fun showToStudy(n: Int) {
        stopProgress()
        val s = Xuexi.today()
        val b = base(ROLL)
            .setContentTitle(Copy.toStudyTitle(n, System.currentTimeMillis() / 60_000))
            .setContentText(s?.nextText?.let { "带你去：$it" } ?: "带你去学习")
            .setShowWhen(false)
            .setOnlyAlertOnce(false)
            .addAction(studyAction("kick"))
        if (Build.VERSION.SDK_INT >= 36) b.setShortCriticalText("学会儿习")
        nm().notifySafe(b.build())
        handler.postDelayed(cancelRunnable, 6000)
    }

    /** 学习那边学完一节：金色笑眼带星星，「学完一节！今天多 10 分钟」 */
    fun showLesson(title: String?, done: Int, daily: Boolean) {
        stopProgress()
        val b = base(PROUD)
            .setContentTitle(if (daily) "学完一节！今天多 10 分钟" else "学完一节，好样的")
            .setContentText(listOfNotNull(title, "今天第 $done 节").joinToString(" · "))
            .setShowWhen(false)
            .setOnlyAlertOnce(false)
        if (Build.VERSION.SDK_INT >= 36) b.setShortCriticalText(if (daily) "+10分钟" else "学完一节")
        nm().notifySafe(b.build())
        handler.postDelayed(cancelRunnable, 4500)
    }

    fun cancel() {
        stopProgress()
        handler.removeCallbacks(cancelRunnable)
        handler.removeCallbacks(verifyRunnable)
        lastState = ""
        runCatching { nm().cancel(ID) }.onFailure { Journal.error("收岛", it) }
    }

    private fun stopProgress() {
        handler.removeCallbacks(progressRunnable)
        handler.removeCallbacks(cancelRunnable)
        handler.removeCallbacks(quizRunnable)
        quizOn = null
        catch = null
    }

    private fun task(): String? = Prefs.task.value.trim().ifEmpty { null }

    private fun postCatch(alert: Boolean) {
        val c = catch ?: return
        val now = System.currentTimeMillis()
        val elapsedSec = ((now - c.trackStart) / 1000).coerceAtLeast(0)
        val app = AppCatalog.label(appCtx, c.pkg)
        val today = Engine.todaySec()
        val overDaily = c.dailySec > 0 && today >= c.dailySec
        // 今天总时长超了，眼神至少是不耐烦
        val mood = Copy.mood(elapsedSec, c.thresholdSec, c.snoozed).let { if (overDaily) maxOf(it, 1) else it }
        val study = if (Xuexi.installed()) Xuexi.today() else null
        val left = study?.left ?: 0
        val title = Copy.catchTitle(elapsedSec, c.thresholdSec, c.snoozedJustNow, app, task(), c.seed, overDaily, today, left)
        val fuse = fuseState(c, now)
        // 什么都没变就不发，免得系统白白重画
        val chip = if (Rom.chipRunsTimer) null else Copy.chip(if (overDaily) today else elapsedSec)
        val major = "$mood|$title|${fuse.second}|${task()}|$chip|$left|${c.snoozed}"
        val key = "$major|${fuse.first / FUSE_STEP}"
        // 最多隔 15 秒也要发一次，给自动过期续命
        val keepAlive = now - lastPostAt >= KEEPALIVE_MS
        if (!alert && !keepAlive && key == lastKey) return
        // 只有导火索挪了一点：至少隔 3 秒再发，别让系统觉得发得太勤
        if (!alert && !keepAlive && major == lastMajor && now - lastPostAt < MIN_GAP_MS) return
        lastKey = key
        lastMajor = major
        lastPostAt = now

        val b = base(LIVE[mood], if (mood == 2) CHANNEL_ANGRY else CHANNEL)
            .setContentTitle(title)
            .setContentText(Copy.catchText(app, task(), if (overDaily) today else null, left, study?.min ?: 0))
            // 超了每天上限时，胶囊里的计时显示今天一共刷了多久
            .setWhen(if (overDaily) now - today * 1000 else c.trackStart)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOnlyAlertOnce(!alert)
            .setTimeoutAfter(WATCHDOG_MS)
            // 装了学习：按钮换成「去学一节」，直接打开下一节
            .addAction(if (Xuexi.installed()) studyAction("island") else action(ActionReceiver.ACTION_BACK, "回去专注", 1))
        // 不认秒表的岛（ColorOS、HyperOS、OriginOS 6）给它短字，不然胶囊里会显示 App 名或整句标题
        if (chip != null && Build.VERSION.SDK_INT >= 36) b.setShortCriticalText(chip)
        if (!c.snoozed) b.addAction(action(ActionReceiver.ACTION_SNOOZE, "再刷 5 分钟", 2))
        else if (Xuexi.installed()) b.addAction(action(ActionReceiver.ACTION_BACK, "回去专注", 1))
        // 超时太多：右边的分心 App 图标换成一张发抖的红色怒脸
        if (mood == 2) {
            b.setLargeIcon(Icon.createWithResource(appCtx, FACE))
        } else {
            AppCatalog.iconRef(appCtx, c.pkg)?.let { b.setLargeIcon(it) }
        }
        if (Build.VERSION.SDK_INT >= 36) b.setStyle(fuseStyle(mood, fuse))
        nm().notifySafe(b.build())
        // 每次发完都核对系统有没有把它放进岛；中途被降级、被撤掉都能看出来，变了才记
        handler.removeCallbacks(verifyRunnable)
        handler.postDelayed(verifyRunnable, 600)
        if (!verified) {
            verified = true
            handler.postDelayed({ logIslandState("岛亮起后") }, 1500)
        }
    }

    private val verifyRunnable = Runnable { runCatching { verifyPromoted() } }

    /** vivo 会把岛现在归谁、在哪写进系统设置；别家读不到就不记 */
    private fun logIslandState(at: String) {
        val raw = Diag.secure(appCtx, "island_state_json") ?: return
        Journal.add(Journal.Kind.Island, "$at vivo 岛状态：$raw")
    }

    /** 发出去以后看系统有没有真把它放进岛；没放进去多半是「显示实时活动」没开 */
    private var lastState = ""

    private fun verifyPromoted() {
        val sbn = runCatching { nm().activeNotifications.firstOrNull { it.id == ID } }.getOrNull()
        val state = when {
            sbn == null -> "gone"
            Build.VERSION.SDK_INT >= 36 && (sbn.notification.flags and Notification.FLAG_PROMOTED_ONGOING) != 0 -> "in"
            else -> "out"
        }
        if (state == lastState) return
        lastState = state
        when (state) {
            "in" -> {
                _lastPromoted.value = true
                Journal.add(Journal.Kind.Island, "系统把提醒放进了${Rom.islandName}（第 $posts 次发）")
            }
            "out" -> {
                _lastPromoted.value = false
                val n = sbn!!.notification
                val canPromote = Build.VERSION.SDK_INT >= 36 && runCatching { nm().canPostPromotedNotifications() }.getOrDefault(false)
                val promotable = Build.VERSION.SDK_INT >= 36 && runCatching { n.hasPromotableCharacteristics() }.getOrDefault(false)
                Journal.add(
                    Journal.Kind.Island,
                    "系统没把提醒放进${Rom.islandName}，只当普通通知：系统允许${Diag.yn(canPromote)} 格式合规${Diag.yn(promotable)} 权限项 ${Perms.promotionOp(appCtx)}",
                )
            }
            "gone" -> if (catch != null) Journal.add(Journal.Kind.Island, "岛还在算，但通知不见了（被系统撤掉或被划掉）")
        }
    }

    private val _lastPromoted = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)

    /** 最近一次亮岛有没有真的进岛；null = 还没亮过 */
    val lastPromoted: kotlinx.coroutines.flow.StateFlow<Boolean?> = _lastPromoted

    private fun nm() = appCtx.getSystemService(NotificationManager::class.java)

    private fun NotificationManager.notifySafe(n: Notification) {
        try {
            notify(ID, n)
            posts++
        } catch (e: Exception) {
            Journal.error("发岛", e)
        }
    }

    private var posts = 0

    private fun base(smallIcon: Int, channel: String = CHANNEL): Notification.Builder {
        val open = PendingIntent.getActivity(
            appCtx, 0,
            Intent(appCtx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(appCtx, channel)
            .setSmallIcon(smallIcon)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    appCtx, 9,
                    Intent(appCtx, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_DISMISSED),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .setTimeoutAfter(WATCHDOG_MS)
            .addExtras(Bundle().apply {
                putBoolean(EXTRA_REQUEST_PROMOTED, true)
                // 三星要这个标记才进 Now Bar
                if (Rom.needsSamsungExtra) {
                    putBoolean("android.ongoingActivityNoti.automation", true)
                    putString("android.ongoingActivityNoti.automationPackage", appCtx.packageName)
                }
            })
    }

    private fun action(act: String, label: String, req: Int): Notification.Action {
        val pi = PendingIntent.getBroadcast(
            appCtx, req,
            Intent(appCtx, ActionReceiver::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(appCtx, R.drawable.ic_eyes), label, pi).build()
    }

    private fun studyAction(from: String): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(appCtx, R.drawable.ic_eyes), "去学一节", Xuexi.pending(appCtx, from)).build()

    /**
     * 导火索：一根细线，烧到头岛就跳一下（升一档）。
     * 如果在这一段里就要被关掉，线头换成红 ✕，烧到头就关。
     * 走过的亮，前面的暗；眼睛骑在线上往前走。
     */
    /** 导火索走到哪（0..1000）、线头是 💢 还是 ✕ */
    private fun fuseState(c: Catch, now: Long): Pair<Int, Int> {
        val t = c.thresholdSec.coerceAtLeast(1) * 1000L
        val elapsed = now - c.trackStart
        val levelStart = c.trackStart + (elapsed / t) * t
        var end = levelStart + t
        var endIcon = R.drawable.anger_mark
        if (c.closeAt > 0 && c.closeAt <= end) {
            end = c.closeAt
            endIcon = R.drawable.close_mark
        }
        val start = minOf(levelStart, end - 1000)
        val p = (((now - start).toDouble() / (end - start)) * 1000).toInt().coerceIn(0, 1000)
        return p to endIcon
    }

    private fun fuseStyle(mood: Int, fuse: Pair<Int, Int>): Notification.Style =
        Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(1000).setColor(FUSE[mood])))
            .setProgress(fuse.first)
            .setProgressTrackerIcon(Icon.createWithResource(appCtx, RIDER[mood]))
            .setProgressEndIcon(Icon.createWithResource(appCtx, fuse.second))
}
