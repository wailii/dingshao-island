package com.xiaodeng.dingshao

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机或更新完以后把进程拉起来；无障碍服务由系统接着连，盯梢中的话就接着盯 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Prefs.init(ctx)
        Journal.init(ctx)
        Engine.attach(ctx)
        val what = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> "手机开机"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "盯梢岛更新完成"
            else -> intent.action ?: "?"
        }
        Journal.add(Journal.Kind.Service, "$what，盯梢岛已就位")
    }
}
