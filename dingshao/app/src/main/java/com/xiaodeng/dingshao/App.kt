package com.xiaodeng.dingshao

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Journal.init(this)
        IslandNotifier.ensureChannel(this)
        Engine.attach(this)
        Journal.add(Journal.Kind.Service, "盯梢岛进程启动；上次结束：${Diag.lastExit(this) ?: "（系统没留记录）"}")
        // 每天第一次启动时记一份现场，跨品牌对比用
        val today = java.time.LocalDate.now().toString()
        if (!Prefs.flag("snap_$today")) {
            Prefs.setFlag("snap_$today")
            Journal.add(Journal.Kind.Device, Diag.device(this).trim())
            Journal.add(Journal.Kind.Device, Diag.state(this).trim())
        }
    }
}
