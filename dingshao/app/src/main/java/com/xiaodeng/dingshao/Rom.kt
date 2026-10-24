package com.xiaodeng.dingshao

import android.os.Build

/**
 * 各家手机的岛都接安卓 16 的同一套「实况通知」，差别在细节上，集中写在这里：
 * 岛叫什么、开关在哪、胶囊里能不能自己走秒表、能不能放动图图标、要不要额外标记。
 *
 * 依据：vivo OriginOS 7 真机实测；OPPO ColorOS 16、小米 HyperOS 3、vivo OriginOS 6（海外版）、
 * 荣耀 MagicOS 10、三星 One UI 8.5 来自 tigerduck-app/android-dynamic-island-test 的逐台实测。
 */
object Rom {

    enum class Vendor { Vivo, Oppo, Xiaomi, Honor, Samsung, Huawei, Other }

    val vendor: Vendor by lazy {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        when {
            "vivo" in m || "iqoo" in m -> Vendor.Vivo
            "oppo" in m || "oneplus" in m || "realme" in m -> Vendor.Oppo
            "xiaomi" in m || "redmi" in m || "poco" in m -> Vendor.Xiaomi
            "honor" in m -> Vendor.Honor
            "samsung" in m -> Vendor.Samsung
            "huawei" in m -> Vendor.Huawei
            else -> Vendor.Other
        }
    }

    /** 系统层面支不支持实况通知（安卓 16 起才有） */
    val liveSupported: Boolean get() = Build.VERSION.SDK_INT >= 36

    val romName: String by lazy {
        when (vendor) {
            Vendor.Vivo -> prop("ro.vivo.os.build.display.id").ifEmpty { "OriginOS" }
            Vendor.Oppo -> prop("ro.build.version.oplusrom.display").ifEmpty { prop("ro.build.version.oplusrom") }
                .let { if (it.isEmpty()) "ColorOS" else "ColorOS $it".replace("ColorOS V", "ColorOS ") }
            Vendor.Xiaomi -> prop("ro.mi.os.version.name").let { if (it.isEmpty()) "HyperOS" else "HyperOS $it" }
            Vendor.Honor -> prop("ro.build.version.magic").ifEmpty { "MagicOS" }
            Vendor.Samsung -> "One UI"
            Vendor.Huawei -> "HarmonyOS"
            Vendor.Other -> "Android"
        }
    }

    /** 这家的岛叫什么，界面文案里用 */
    val islandName: String
        get() = when (vendor) {
            Vendor.Vivo -> "原子岛"
            Vendor.Oppo -> "流体云"
            Vendor.Xiaomi -> "超级岛"
            Vendor.Honor -> "胶囊"
            Vendor.Samsung -> "Now Bar"
            else -> "状态栏胶囊"
        }

    /** 让提醒进岛的那个开关，在系统设置里叫什么（找不到就是不用开） */
    val liveSwitchName: String
        get() = when (vendor) {
            Vendor.Vivo -> "显示实时活动"
            Vendor.Oppo -> "在流体云中显示实况"
            else -> "实时活动 / 实况通知"
        }

    /** OriginOS 7 是真机验证过的：胶囊里的秒表会自己走、动图图标会自己播 */
    private val vivoOs7: Boolean by lazy {
        vendor == Vendor.Vivo && (Build.VERSION.SDK_INT >= 37 || "7" in prop("ro.vivo.os.build.display.id"))
    }

    /**
     * 胶囊里能不能让系统自己走秒表。
     * ColorOS、HyperOS、OriginOS 6 不认秒表，会显示 App 名或整句标题，所以要给它短字（「3分钟」）。
     */
    val chipRunsTimer: Boolean
        get() = when (vendor) {
            Vendor.Vivo -> vivoOs7
            Vendor.Oppo, Vendor.Xiaomi -> false
            else -> true
        }

    /** 动图图标只在 OriginOS 7 上验证过，别家先用静态图标 */
    val animatedIcons: Boolean get() = vivoOs7

    /** 三星要多带一个它自己的标记才进 Now Bar */
    val needsSamsungExtra: Boolean get() = vendor == Vendor.Samsung

    fun prop(key: String): String = try {
        Runtime.getRuntime().exec(arrayOf("getprop", key)).inputStream.bufferedReader().use { it.readText().trim() }
    } catch (_: Exception) {
        ""
    }
}
