package com.xiaodeng.islandprobe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper

object Probe {
    const val CHANNEL = "island_probe"
    const val NOTIFY_ID = 7
    private const val PREFS = "probe"

    // API 36 的 Builder 还没有 setRequestPromotedOngoing，只能走 extra
    private const val EXTRA_REQUEST_PROMOTED = "android.requestPromotedOngoing"

    data class Variant(val key: String, val name: String, val desc: String)

    val variants = listOf(
        Variant("basic", "① 基础", "胶囊里写「2分钟」，卡片里一行标题一行正文"),
        Variant("large", "② 加彩色大图标", "在 ① 上加一张橙底眼睛大图，看岛和卡片用不用它"),
        Variant("progress", "③ 进度条", "灰色段是阈值以内，橙色段是超出的时间，眼睛跟着进度走"),
        Variant("actions", "④ 带按钮", "卡片里两个按钮：「回去专注」「再给我 5 分钟」"),
        Variant("ticking", "⑤ 每 5 秒更新", "胶囊里的时间每 5 秒跳一次，看更新时岛会不会闪"),
        Variant("chrono", "⑥ 系统自己计时", "不写胶囊文字，交给系统秒表走时间，看岛认不认"),
    )

    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "岛测试", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "测试原子岛用"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    fun post(ctx: Context, key: String) {
        stopTicker()
        if (key == "ticking") startTicker(ctx) else show(ctx, key, build(ctx, key))
    }

    fun cancelAll(ctx: Context) {
        stopTicker()
        ctx.getSystemService(NotificationManager::class.java).cancel(NOTIFY_ID)
    }

    private fun build(ctx: Context, key: String): Notification = when (key) {
        "large" -> base(ctx, "2分钟了，就我还在这守着", "专注任务：读完论文")
            .shortText("2分钟")
            .setLargeIcon(Icon.createWithBitmap(mascot()))
            .build()

        "progress" -> base(ctx, "超出阈值 1 分钟", "专注任务：读完论文 · 阈值 3 分钟")
            .shortText("超1分")
            .apply { if (Build.VERSION.SDK_INT >= 36) setStyle(progressStyle(ctx)) }
            .build()

        "actions" -> base(ctx, "2分钟了，就我还在这守着", "专注任务：读完论文")
            .shortText("2分钟")
            .addAction(action(ctx, "回去专注", 1))
            .addAction(action(ctx, "再给我 5 分钟", 2))
            .build()

        "chrono" -> base(ctx, "系统自己计时", "专注任务：读完论文")
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setUsesChronometer(true)
            .build()

        else -> base(ctx, "2分钟了，就我还在这守着", "专注任务：读完论文")
            .shortText("2分钟")
            .build()
    }

    private fun base(ctx: Context, title: String, text: String): Notification.Builder {
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_eyes)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(open)
            .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED, true) })
    }

    private fun Notification.Builder.shortText(s: String): Notification.Builder = apply {
        if (Build.VERSION.SDK_INT >= 36) setShortCriticalText(s)
    }

    private fun progressStyle(ctx: Context): Notification.Style =
        Notification.ProgressStyle()
            .setStyledByProgress(false)
            .setProgressSegments(
                listOf(
                    Notification.ProgressStyle.Segment(60).setColor(0xFFB9B9B4.toInt()),
                    Notification.ProgressStyle.Segment(40).setColor(0xFFFF5A1F.toInt()),
                )
            )
            .setProgressPoints(listOf(Notification.ProgressStyle.Point(60).setColor(0xFF111111.toInt())))
            .setProgress(78)
            .setProgressTrackerIcon(Icon.createWithResource(ctx, R.drawable.ic_eyes))

    private fun action(ctx: Context, label: String, req: Int): Notification.Action {
        val pi = PendingIntent.getBroadcast(
            ctx, req,
            Intent(ctx, ActionReceiver::class.java).putExtra(ActionReceiver.EXTRA_LABEL, label),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_eyes), label, pi).build()
    }

    private fun startTicker(ctx: Context) {
        val app = ctx.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java)
        var step = 0
        val r = object : Runnable {
            override fun run() {
                val sec = step * 5
                val n = base(app, "已经刷了 ${clock(sec)}", "专注任务：读完论文")
                    .shortText(clock(sec))
                    .build()
                if (step == 0) show(app, "ticking", n) else nm.notify(NOTIFY_ID, n)
                step++
                if (sec < 180) handler.postDelayed(this, 5000)
            }
        }
        ticker = r
        handler.post(r)
    }

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }

    private fun clock(sec: Int): String = if (sec < 60) "${sec}秒" else "${sec / 60}分${sec % 60}秒"

    private fun show(ctx: Context, key: String, n: Notification) {
        val promotable = Build.VERSION.SDK_INT >= 36 && n.hasPromotableCharacteristics()
        ctx.getSystemService(NotificationManager::class.java).notify(NOTIFY_ID, n)
        handler.postDelayed({ record(ctx, key, promotable) }, 700)
    }

    private fun record(ctx: Context, key: String, promotable: Boolean) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val sbn = nm.activeNotifications.firstOrNull { it.id == NOTIFY_ID }
        val status = when {
            sbn == null -> Result.MISSING
            Build.VERSION.SDK_INT >= 36 &&
                (sbn.notification.flags and Notification.FLAG_PROMOTED_ONGOING) != 0 -> Result.PROMOTED
            else -> Result.PLAIN
        }
        prefs(ctx).edit()
            .putString("r_$key", "${status.name}|$promotable")
            .apply()
    }

    enum class Result { PROMOTED, PLAIN, MISSING }

    data class Record(val result: Result, val promotable: Boolean)

    fun recordOf(ctx: Context, key: String): Record? {
        val raw = prefs(ctx).getString("r_$key", null) ?: return null
        val parts = raw.split("|")
        return Record(Result.valueOf(parts[0]), parts.getOrNull(1) == "true")
    }

    fun markAction(ctx: Context, label: String) {
        prefs(ctx).edit().putString("tapped_action", label).apply()
    }

    fun tappedAction(ctx: Context): String? = prefs(ctx).getString("tapped_action", null)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun mascot(): Bitmap {
        val s = 192
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFFF5A1F.toInt()
        c.drawCircle(96f, 96f, 96f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(68f, 94f, 27f, p)
        c.drawCircle(126f, 99f, 31f, p)
        p.color = 0xFF111111.toInt()
        c.drawCircle(79f, 101f, 10.5f, p)
        c.drawCircle(138f, 106f, 11.5f, p)
        return bmp
    }
}
