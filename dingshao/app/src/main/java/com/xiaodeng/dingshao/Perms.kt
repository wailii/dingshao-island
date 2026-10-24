package com.xiaodeng.dingshao

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object Perms {

    data class State(
        val notify: Boolean,
        val live: Boolean,
        val a11y: Boolean,
        val battery: Boolean,
        /** 无障碍开着，但服务没连上：多半是进程死过一次，系统不再自动拉起 */
        val a11yStuck: Boolean = false,
        /**
         * vivo：无障碍设置页开着时，会把第三方无障碍服务全部暂停（列表里显示「已关闭」），
         * 离开设置几秒后自动恢复。刚从那边回来的这一小会儿，别跟用户说「被关了」。
         */
        val a11yRecovering: Boolean = false,
    ) {
        val a11yOk: Boolean get() = a11y && !a11yStuck
        /** 通知和无障碍是必须的；「进岛」那个开关不是每家都有，不拦人 */
        val ready: Boolean get() = notify && a11y
        val missing: Int get() = listOf(notify, a11y).count { !it }
    }

    fun read(ctx: Context): State {
        val a11y = accessibility(ctx)
        val stuck = a11y && !running(ctx)
        val ok = a11y && !stuck
        val now = System.currentTimeMillis()
        if (ok) a11yBackAt = 0L
        return State(
            notify = notifications(ctx),
            live = liveUpdates(ctx),
            a11y = a11y,
            battery = battery(ctx),
            a11yStuck = stuck,
            a11yRecovering = !ok && Rom.vendor == Rom.Vendor.Vivo && a11yBackAt > 0 && now - a11yBackAt < RECOVER_MS,
        )
    }

    private const val RECOVER_MS = 20_000L

    /** 我们把用户送去无障碍设置的时间 */
    @Volatile var a11yVisitAt = 0L

    /** 用户从无障碍设置回到盯梢岛的时间（之后 20 秒内算「正在恢复」） */
    @Volatile var a11yBackAt = 0L

    /** 回到盯梢岛时调：刚去过无障碍设置，或者服务刚被停掉，就开始等它恢复 */
    fun onAppResumed() {
        val now = System.currentTimeMillis()
        val lost = Engine.lastLostAt
        if (a11yVisitAt > 0 || (lost > 0 && now - lost < 10 * 60_000L)) {
            a11yBackAt = now
            a11yVisitAt = 0L
        }
    }

    /** 系统真正连着的无障碍服务里有没有我们 */
    private fun running(ctx: Context): Boolean {
        val am = ctx.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        val me = ComponentName(ctx, WatchService::class.java)
        return am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.let { si -> si.packageName == me.packageName && si.name == me.className } == true }
    }

    fun notifications(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    fun liveUpdates(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 36 &&
            ctx.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()

    fun accessibility(ctx: Context): Boolean {
        val me = ComponentName(ctx, WatchService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    fun battery(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    /** vivo OriginOS 7 的实况白名单里有没有我们；读不到（别家手机、或系统不让读）就是 null */
    fun inVivoWhitelist(ctx: Context): Boolean? = runCatching {
        Settings.Secure.getString(ctx.contentResolver, "liveupdate_whitelist_pkgs")
    }.getOrNull()?.let { raw -> raw.split(',').map { it.trim() }.contains(ctx.packageName) }

    /** 「进岛」那个权限操作项的状态，给自检看 */
    fun promotionOp(ctx: Context): String = try {
        val mode = ctx.getSystemService(android.app.AppOpsManager::class.java)
            .unsafeCheckOpNoThrow("android:post_promoted_notifications", android.os.Process.myUid(), ctx.packageName)
        when (mode) {
            android.app.AppOpsManager.MODE_ALLOWED -> "允许"
            android.app.AppOpsManager.MODE_IGNORED -> "关着"
            android.app.AppOpsManager.MODE_DEFAULT -> "默认"
            else -> "其他($mode)"
        }
    } catch (_: Exception) {
        "读不到"
    }

    /**
     * 按顺序试，能打开哪个就打开哪个。
     * 从界面里跳时不另开任务：不然会落进系统设置原有的任务里，按返回在设置里一层层退，回不到盯梢岛。
     */
    fun openFirst(ctx: Context, intents: List<Intent>): Boolean {
        val fromActivity = ctx is android.app.Activity
        for (i in intents) {
            val ok = runCatching {
                if (!fromActivity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    private fun comp(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

    /** 「进岛」开关：没有专门页面的手机退到这个 App 的通知设置 */
    fun liveIntents(ctx: Context): List<Intent> = listOf(liveUpdatesIntent(ctx), notificationSettingsIntent(ctx))

    /** 后台别被清理：各家的「后台耗电 / 电池优化」页 */
    fun backgroundIntents(ctx: Context): List<Intent> = buildList {
        when (Rom.vendor) {
            // vivo 的「后台耗电管理」列表页不让外部直接打开，只能先到「电池」页再点进去
            Rom.Vendor.Vivo -> add(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            Rom.Vendor.Oppo -> add(comp("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"))
            Rom.Vendor.Xiaomi -> add(comp("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                .putExtra("package_name", ctx.packageName).putExtra("package_label", ctx.getString(R.string.app_name)))
            else -> {}
        }
        add(batteryIntent(ctx))
        add(appInfoIntent(ctx))
    }

    /** 自启动：各家手机管家里的「自启动管理」 */
    fun autostartIntents(ctx: Context): List<Intent> = buildList {
        when (Rom.vendor) {
            Rom.Vendor.Vivo -> add(comp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"))
            Rom.Vendor.Oppo -> {
                add(comp("com.oplus.safecenter", "com.oplus.safecenter.startupapp.view.StartupAppListActivity"))
                add(comp("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"))
            }
            Rom.Vendor.Xiaomi -> add(comp("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            Rom.Vendor.Honor -> add(comp("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
            Rom.Vendor.Huawei -> add(comp("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
            else -> {}
        }
        add(appInfoIntent(ctx))
    }

    fun liveUpdatesIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)

    fun notificationSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)

    fun accessibilityIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun appInfoIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    @Suppress("BatteryLife")
    fun batteryIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
}
