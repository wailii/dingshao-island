package com.xiaodeng.dingshao

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object AppCatalog {

    data class Entry(val pkg: String, val label: String, val icon: Bitmap?)

    /** 常见的分心 App，排在选择列表最前面 */
    val SUGGESTED = listOf(
        "com.xingin.xhs", "com.ss.android.ugc.aweme", "com.sina.weibo", "tv.danmaku.bili",
        "com.zhihu.android", "com.smile.gifmaker", "com.kuaishou.nebula", "com.ss.android.ugc.aweme.lite",
        "com.ss.android.article.news", "com.baidu.tieba", "com.taobao.taobao", "com.xunmeng.pinduoduo",
        "com.tencent.qqlive", "com.qiyi.video", "com.youku.phone", "com.hunantv.imgo.activity",
        "com.phoenix.read", "com.dragon.read", "com.max.xiaoheihe", "com.tencent.tmgp.sgame",
        "com.shizhuang.duapp", "com.taptap", "com.tencent.news", "com.kaixinkan.ugc.video",
    )

    /** 第一次打开时默认帮你勾上的 */
    private val DEFAULT_ON = listOf(
        "com.xingin.xhs", "com.ss.android.ugc.aweme", "com.sina.weibo", "tv.danmaku.bili",
        "com.zhihu.android", "com.smile.gifmaker", "com.kuaishou.nebula",
    )

    private val labels = ConcurrentHashMap<String, String>()
    private val icons = ConcurrentHashMap<String, Bitmap>()
    @Volatile private var all: List<Entry>? = null

    fun loadAll(ctx: Context): List<Entry> {
        all?.let { return it }
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.CHINA)
        val list = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != ctx.packageName }
            .map { pkg -> Entry(pkg, label(ctx, pkg), icon(ctx, pkg)) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
        all = list
        return list
    }

    fun defaultWatched(ctx: Context): Set<String> =
        DEFAULT_ON.filter { isInstalled(ctx, it) }.toSet()

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** 只读这几个 App，主页一打开就要显示，不能等全量列表 */
    fun entries(ctx: Context, pkgs: Collection<String>): List<Entry> {
        val collator = Collator.getInstance(Locale.CHINA)
        return pkgs.filter { isInstalled(ctx, it) }
            .map { Entry(it, label(ctx, it), icon(ctx, it)) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun label(ctx: Context, pkg: String): String = labels.getOrPut(pkg) {
        try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    /** 让系统直接读那个 App 自己的图标资源，比传位图清楚，也不用每次重发都带一张图 */
    fun iconRef(ctx: Context, pkg: String): android.graphics.drawable.Icon? = try {
        val res = ctx.packageManager.getApplicationInfo(pkg, 0).icon
        if (res != 0) android.graphics.drawable.Icon.createWithResource(pkg, res)
        else icon(ctx, pkg)?.let { android.graphics.drawable.Icon.createWithBitmap(it) }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    fun icon(ctx: Context, pkg: String, size: Int = 144): Bitmap? {
        icons[pkg]?.let { return it }
        val d = try {
            ctx.packageManager.getApplicationIcon(pkg)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val bmp = d.toBitmap(size)
        icons[pkg] = bmp
        return bmp
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        setBounds(0, 0, size, size)
        draw(c)
        return bmp
    }
}
