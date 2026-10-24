package com.xiaodeng.dingshao.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.xiaodeng.dingshao.Diag
import com.xiaodeng.dingshao.Journal
import com.xiaodeng.dingshao.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 日志：给开发者分析问题用。连点主页左上角「盯梢岛」10 次才出现入口 */
@Composable
fun LogScreen(onBack: () -> Unit, onCheck: () -> Unit) {
    val ctx = LocalContext.current
    val all by Journal.entries.collectAsState()
    var filter by remember { mutableStateOf<Journal.Kind?>(null) }
    var showSnap by remember { mutableStateOf(false) }
    val list = remember(all, filter) { all.asReversed().filter { filter == null || it.kind == filter } }
    val time = remember { SimpleDateFormat("HH:mm:ss", Locale.CHINA) }
    val day = remember { SimpleDateFormat("M 月 d 日", Locale.CHINA) }
    val errors = remember(all) { all.count { it.kind == Journal.Kind.Error } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            T("←", 24, Palette.Ink, modifier = Modifier.pressable(onClick = onBack).padding(8.dp))
        }
        Row(Modifier.padding(start = 22.dp, end = 22.dp), verticalAlignment = Alignment.Bottom) {
            T("日志", 30, weight = FontWeight.Bold)
            T(
                "共 ${all.size} 条${if (errors > 0) " · 出错 $errors" else ""}", 14,
                if (errors > 0) Palette.Accent else Palette.Sub,
                modifier = Modifier.padding(start = 10.dp, bottom = 5.dp).weight(1f),
            )
            T(
                "隐藏入口", 13, Palette.Sub, FontWeight.Medium,
                Modifier
                    .padding(bottom = 6.dp)
                    .pressable {
                        Prefs.setFlag("devlog", false)
                        Journal.add(Journal.Kind.Rule, "在日志页隐藏了日志入口")
                        Toast.makeText(ctx, "日志入口已隐藏，连点左上角「盯梢岛」10 次可以再打开", Toast.LENGTH_LONG).show()
                        onBack()
                    }
                    .padding(6.dp),
            )
        }

        // 现场快照：机型、权限、能力、进程怎么死的
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.Card)
                .pressable(scaleTo = 0.99f) { showSnap = !showSnap }
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("现场快照", 15, Palette.Ink, FontWeight.SemiBold, Modifier.weight(1f))
                T(if (showSnap) "收起" else "展开", 13, Palette.Sub)
            }
            AnimatedVisibility(showSnap, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                val snap = remember(showSnap) {
                    Diag.device(ctx) + "\n" + Diag.state(ctx) + "\n进程最近几次是怎么结束的：\n" +
                        Diag.exits(ctx).ifEmpty { listOf("（系统没留记录）") }.joinToString("\n") { "  $it" }
                }
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    T(snap.trim(), 12, Palette.Sub, lineHeight = 18)
                }
            }
        }

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip("全部", filter == null) { filter = null }
            listOf(Journal.Kind.Island, Journal.Kind.Window, Journal.Kind.App, Journal.Kind.Service, Journal.Kind.Error, Journal.Kind.Device, Journal.Kind.Rule)
                .forEach { k -> FilterChip(Journal.tag(k), filter == k) { filter = if (filter == k) null else k } }
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.Card)
        ) {
            if (list.isEmpty()) item { T("还没有", 15, Palette.Sub, modifier = Modifier.padding(22.dp)) }
            var lastDay = ""
            list.forEach { e ->
                val d = day.format(Date(e.t))
                if (d != lastDay) {
                    lastDay = d
                    item(key = "d$d${e.t}") { T(d, 12, Palette.Sub, FontWeight.SemiBold, Modifier.padding(start = 18.dp, top = 14.dp, bottom = 2.dp)) }
                }
                item(key = "${e.t}${e.kind}${e.text.hashCode()}") {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        T(time.format(Date(e.t)), 12, Palette.Sub, tabular = true, modifier = Modifier.width(62.dp).padding(top = 1.dp))
                        Box(
                            Modifier
                                .padding(top = 5.dp, end = 10.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(kindColor(e.kind))
                        )
                        T(e.text, 13, if (e.kind == Journal.Kind.Error) Palette.Accent else Palette.Ink, lineHeight = 19)
                    }
                }
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("自检", Modifier.weight(1f), dark = false, height = 52.dp, textSize = 15, onClick = onCheck)
            Pill("复制", Modifier.weight(1f), dark = false, height = 52.dp, textSize = 15) {
                // 剪贴板放不下太长的，只放最近的；要全量用「分享文件」
                val text = Journal.export(ctx).let { if (it.length > 150_000) it.take(8_000) + "\n…（太长，前后各截一段，完整的请用分享文件）…\n" + it.takeLast(140_000) else it }
                ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("盯梢岛日志", text))
                Toast.makeText(ctx, "复制好了", Toast.LENGTH_SHORT).show()
            }
            Pill("分享文件", Modifier.weight(1.3f), height = 52.dp, textSize = 15) {
                runCatching {
                    val f = Journal.exportFile(ctx)
                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.logs", f)
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .putExtra(Intent.EXTRA_SUBJECT, f.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(send, "把日志发给…"))
                }.onFailure {
                    Journal.error("分享日志", it)
                    Toast.makeText(ctx, "分享失败，先用「复制」", Toast.LENGTH_SHORT).show()
                }
            }
        }
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun FilterChip(text: String, on: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) Palette.Ink else Palette.Card, tween(160), label = "chip")
    Box(
        Modifier
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) { T(text, 13, if (on) Color.White else Palette.Ink, FontWeight.Medium) }
}

private fun kindColor(k: Journal.Kind) = when (k) {
    Journal.Kind.Island -> Palette.Accent
    Journal.Kind.Service -> Color(0xFF3B6FF5)
    Journal.Kind.Window -> Color(0xFF8E6CEF)
    Journal.Kind.App -> Color(0xFFB9B7B0)
    Journal.Kind.Rule -> Palette.Ink
    Journal.Kind.Error -> Color(0xFFE5483B)
    Journal.Kind.Device -> Palette.Good
}
