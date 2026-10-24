package com.xiaodeng.dingshao

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志里的「现场」：机型、系统、各项权限和能力、进程上次怎么死的。
 * 拿到不同品牌的日志，对着这些就能看出是哪一环出的问题。
 */
object Diag {

    /** 各家系统版本藏在不同的属性里，都读一遍 */
    private val ROM_PROPS = listOf(
        "ro.vivo.os.build.display.id", "ro.vivo.product.version", "ro.vivo.os.version",
        "ro.build.version.oplusrom.display", "ro.build.version.oplusrom",
        "ro.mi.os.version.name", "ro.mi.os.version.code", "ro.miui.ui.version.name",
        "ro.build.version.magic", "ro.build.version.emui", "hw_sc.build.platform.version",
        "ro.build.version.oneui", "ro.build.version.sem",
        "ro.build.display.id",
    )

    fun device(ctx: Context): String = buildString {
        val dm = ctx.resources.displayMetrics
        appendLine("机型：${Build.MANUFACTURER} / ${Build.BRAND} / ${Build.MODEL}（${Build.DEVICE}，${Build.PRODUCT}）")
        appendLine("系统：${Rom.romName} · 判定为 ${Rom.vendor} · 岛叫「${Rom.islandName}」")
        appendLine("安卓：${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）· 安全补丁 ${Build.VERSION.SECURITY_PATCH}")
        appendLine("版本号：${Build.DISPLAY}")
        appendLine("指纹：${Build.FINGERPRINT}")
        ROM_PROPS.forEach { k -> Rom.prop(k).takeIf { it.isNotEmpty() }?.let { appendLine("  $k = $it") } }
        appendLine("屏幕：${dm.widthPixels}×${dm.heightPixels} · ${dm.densityDpi}dpi · ${Locale.getDefault()}")
        val pi = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
        appendLine("盯梢岛：${pi?.versionName}（${pi?.longVersionCode}）· 包名 ${ctx.packageName}")
        appendLine("适配：胶囊秒表${yn(Rom.chipRunsTimer)} 动图图标${yn(Rom.animatedIcons)} 三星标记${yn(Rom.needsSamsungExtra)}")
    }

    fun state(ctx: Context): String = buildString {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val pm = ctx.getSystemService(PowerManager::class.java)
        val am = ctx.getSystemService(ActivityManager::class.java)
        val p = Perms.read(ctx)
        appendLine("通知：允许${yn(p.notify)} 总开关${yn(nm.areNotificationsEnabled())}")
        listOf("catch_v1", "angry_v1").forEach { id ->
            val ch = nm.getNotificationChannel(id)
            appendLine("  渠道 $id：${if (ch == null) "没有" else "级别 ${ch.importance}"}")
        }
        val canPromote = if (Build.VERSION.SDK_INT >= 36) runCatching { nm.canPostPromotedNotifications() }.getOrNull() else null
        appendLine("实况通知：系统允许${canPromote?.let(::yn) ?: "（无此接口）"} 权限项 ${Perms.promotionOp(ctx)}")
        if (Rom.vendor == Rom.Vendor.Vivo) {
            appendLine("  vivo 名单里有盯梢岛：${Perms.inVivoWhitelist(ctx)?.let(::yn) ?: "读不到"}")
            appendLine("  vivo 岛状态：${secure(ctx, "island_state_json") ?: "读不到"}")
        }
        appendLine("无障碍：已开启${yn(p.a11y)} 已连上${yn(!p.a11yStuck && p.a11y)} 服务实例${yn(Engine.serviceUp.value)}")
        appendLine("省电：忽略电池优化${yn(p.battery)} 省电模式${yn(pm.isPowerSaveMode)} 后台受限${yn(am.isBackgroundRestricted)}")
        val bucket = runCatching { ctx.getSystemService(UsageStatsManager::class.java).appStandbyBucket }.getOrNull()
        appendLine("待机分组：${bucketName(bucket)}")
        appendLine(
            "设置：盯梢中${yn(Prefs.monitoring.value)} 阈值${Prefs.thresholdSec.value}s 点开${Prefs.expandSec.value}s " +
                "关掉${Prefs.closeSec.value}s 任务「${Prefs.task.value}」"
        )
        appendLine("盯：${Prefs.watched.value.joinToString(",")}")
        val s = Prefs.stats.value
        appendLine("今天：抓到${s.caught} 回来${s.returned} 赖${s.snoozed} 被关${s.kicked}")
    }

    /** 进程最近几次是怎么结束的：被系统冻结后杀、内存不足、用户强行停止、崩溃…… */
    fun exits(ctx: Context, max: Int = 8): List<String> {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val list = runCatching { am.getHistoricalProcessExitReasons(ctx.packageName, 0, max) }.getOrDefault(emptyList())
        val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
        return list.map { e ->
            // vivo 把划掉卡片、一键清理也报成「内存不足」，描述里带 cleaner
            val name = if (e.description?.contains("cleaner", ignoreCase = true) == true) "被清理（划掉卡片或一键清理）" else exitName(e.reason)
            "${fmt.format(Date(e.timestamp))} $name" +
                (e.description?.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: "") +
                " 当时${importanceName(e.importance)}"
        }
    }

    fun lastExit(ctx: Context): String? = exits(ctx, 1).firstOrNull()

    fun secure(ctx: Context, key: String): String? =
        runCatching { Settings.Secure.getString(ctx.contentResolver, key) }.getOrNull()

    private fun exitName(r: Int) = when (r) {
        ApplicationExitInfo.REASON_ANR -> "无响应(ANR)"
        ApplicationExitInfo.REASON_CRASH -> "崩溃"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "崩溃(native)"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "依赖的进程死了"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "资源用太多被杀"
        ApplicationExitInfo.REASON_EXIT_SELF -> "自己退出"
        ApplicationExitInfo.REASON_FREEZER -> "被冻结后杀掉"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "启动失败"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "内存不足被杀"
        ApplicationExitInfo.REASON_OTHER -> "系统杀掉(其他)"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "权限变化"
        ApplicationExitInfo.REASON_SIGNALED -> "被信号杀掉"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "用户/系统强行停止"
        ApplicationExitInfo.REASON_USER_STOPPED -> "用户停止"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "App 更新"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "App 状态变化"
        else -> "原因$r"
    }

    private fun importanceName(i: Int) = when {
        i <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "前台"
        i <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "前台服务"
        i <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "可见"
        i <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "可感知"
        i <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "后台服务"
        i <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "后台缓存"
        else -> "不重要($i)"
    }

    private fun bucketName(b: Int?) = when (b) {
        null -> "读不到"
        UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "活跃(10)"
        UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "常用(20)"
        UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "偶尔(30)"
        UsageStatsManager.STANDBY_BUCKET_RARE -> "很少(40)"
        UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "受限(45)"
        5 -> "豁免(5)"
        else -> "$b"
    }

    fun yn(b: Boolean) = if (b) "✓" else "✗"
}
