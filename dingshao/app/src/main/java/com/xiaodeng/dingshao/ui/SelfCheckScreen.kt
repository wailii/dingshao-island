package com.xiaodeng.dingshao.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaodeng.dingshao.IslandNotifier
import com.xiaodeng.dingshao.Journal
import com.xiaodeng.dingshao.Perms
import com.xiaodeng.dingshao.Rom

/**
 * 自检：这台手机的岛能不能亮、卡在哪一步。
 * 给朋友用的时候，让他点「发一个测试岛」，再把结果复制发回来，就知道要怎么适配。
 */
@Composable
fun SelfCheckScreen(perms: Perms.State, onBack: () -> Unit, onTest: () -> Unit) {
    val ctx = LocalContext.current
    val promoted by IslandNotifier.lastPromoted.collectAsState()
    var tested by rememberSaveable { mutableStateOf(false) }
    var seen by rememberSaveable { mutableStateOf<Boolean?>(null) }

    val whitelist = if (Rom.vendor == Rom.Vendor.Vivo) Perms.inVivoWhitelist(ctx) else null
    val op = Perms.promotionOp(ctx)
    val rows = buildList {
        add(Triple("系统支持实况通知（安卓 16 起）", Rom.liveSupported.mark(), "安卓 ${Build.VERSION.RELEASE}"))
        add(Triple("通知权限", perms.notify.mark(), ""))
        add(Triple("「${Rom.liveSwitchName}」", perms.live.mark(), "权限项：$op"))
        if (Rom.vendor == Rom.Vendor.Vivo) {
            add(Triple("vivo 实况名单里有盯梢岛", whitelist?.mark() ?: "—", if (whitelist == null) "读不到" else ""))
        }
        add(Triple("无障碍", (perms.a11y && !perms.a11yStuck).mark(), if (perms.a11yStuck) "开着但没连上" else ""))
        add(Triple("测试岛进岛了吗", promoted?.mark() ?: "—", if (promoted == null) "还没测" else ""))
        if (seen != null) add(Triple("你看到岛了吗", seen!!.mark(), ""))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            T("←", 24, Palette.Ink, modifier = Modifier.pressable(onClick = onBack).padding(8.dp))
        }
        T("自检", 30, weight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
        T(
            "${Build.MANUFACTURER} ${Build.MODEL} · ${Rom.romName} · 安卓 ${Build.VERSION.RELEASE}",
            14, Palette.Sub, modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 18.dp),
        )
        Panel {
            rows.forEachIndexed { i, (name, mark, note) ->
                Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(name, 15, Palette.Ink)
                        if (note.isNotEmpty()) T(note, 12, Palette.Sub)
                    }
                    T(
                        mark, 16,
                        when (mark) {
                            "✓" -> Palette.Good
                            "✗" -> Palette.Accent
                            else -> Palette.Sub
                        },
                        FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Pill("发一个测试岛", Modifier.fillMaxWidth(), height = 56.dp) {
            tested = true
            seen = null
            onTest()
        }
        AnimatedVisibility(tested && seen == null, enter = fadeIn() + expandVertically()) {
            Column(Modifier.padding(top = 16.dp)) {
                T("摄像头那里亮了吗？", 16, Palette.Ink, FontWeight.SemiBold, Modifier.padding(start = 6.dp, bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("亮了", Modifier.weight(1f), dark = false, height = 50.dp) {
                        seen = true
                        Journal.add(Journal.Kind.Island, "自检：用户说测试岛亮了")
                    }
                    Pill("没亮", Modifier.weight(1f), dark = false, height = 50.dp) {
                        seen = false
                        Journal.add(Journal.Kind.Island, "自检：用户说测试岛没亮")
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Pill("复制自检结果", Modifier.fillMaxWidth(), dark = false, height = 56.dp) {
            val text = buildString {
                append("盯梢岛自检\n")
                append("${Build.MANUFACTURER} ${Build.MODEL} · ${Rom.romName} · 安卓 ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）\n")
                append("版本号：${Build.DISPLAY}\n")
                rows.forEach { (n, m, note) -> append("$m $n${if (note.isNotEmpty()) "（$note）" else ""}\n") }
                append("\n")
                append(Journal.export(ctx))
            }
            ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("盯梢岛自检", text))
            Toast.makeText(ctx, "复制好了，粘贴发出去就行", Toast.LENGTH_SHORT).show()
        }
        Spacer(Modifier.height(18.dp))
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}

private fun Boolean.mark() = if (this) "✓" else "✗"
