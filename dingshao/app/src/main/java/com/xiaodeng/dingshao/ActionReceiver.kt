package com.xiaodeng.dingshao

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Prefs.init(ctx)
        Journal.init(ctx)
        Engine.attach(ctx)
        when (intent.action) {
            ACTION_BACK -> {
                Journal.add(Journal.Kind.Island, "你在岛上点了「回去专注」")
                Engine.backToFocus()
            }
            ACTION_SNOOZE -> Engine.askSnooze()
            ACTION_TRUE -> Engine.answerQuiz(true)
            ACTION_FALSE -> Engine.answerQuiz(false)
            ACTION_DISMISSED -> Journal.add(Journal.Kind.Island, "岛被划掉/清除了（系统或你）")
        }
    }

    companion object {
        const val ACTION_BACK = "com.xiaodeng.dingshao.BACK"
        const val ACTION_SNOOZE = "com.xiaodeng.dingshao.SNOOZE"
        const val ACTION_DISMISSED = "com.xiaodeng.dingshao.DISMISSED"
        const val ACTION_TRUE = "com.xiaodeng.dingshao.TRUE"
        const val ACTION_FALSE = "com.xiaodeng.dingshao.FALSE"
    }
}
