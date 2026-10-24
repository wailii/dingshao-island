package com.xiaodeng.islandprobe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_LABEL) ?: return
        Probe.cancelAll(ctx)
        Probe.markAction(ctx, label)
        Toast.makeText(ctx, "点到了「$label」", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_LABEL = "label"
    }
}
