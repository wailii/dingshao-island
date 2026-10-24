package com.xiaodeng.dingshao.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaodeng.dingshao.AppCatalog
import com.xiaodeng.dingshao.Perms
import com.xiaodeng.dingshao.Prefs
import com.xiaodeng.dingshao.Rom

@Composable
fun SetupScreen(
    perms: Perms.State,
    canClose: Boolean,
    onClose: () -> Unit,
    onDone: () -> Unit,
    onCheck: () -> Unit,
) {
    val ctx = LocalContext.current
    var guide by remember { mutableStateOf<Guide?>(null) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    // 「去过」的小标记变了要重画
    var visits by remember { mutableIntStateOf(0) }

    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val show: (Guide, () -> Unit) -> Unit = { g, action ->
        guide = g
        pending = action
    }
    val go: () -> Unit = {
        val g = guide
        val a = pending
        guide = null
        pending = null
        a?.invoke()
        g?.let { Toast.makeText(ctx, it.toast, Toast.LENGTH_LONG).show() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Palette.Bg)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (canClose) T("←", 24, Palette.Ink, modifier = Modifier.pressable(onClick = onClose).padding(8.dp))
            }
            Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 26.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(150.dp, 62.dp)
                        .shadow(18.dp, RoundedCornerShape(31.dp), ambientColor = Color(0x33000000), spotColor = Color(0x44000000))
                        .clip(RoundedCornerShape(31.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Eyes(Modifier.size(96.dp, 46.dp), if (perms.ready) EyeMood.Watching else EyeMood.Curious)
                }
            }
            T(
                if (perms.ready) "权限都开好了" else "先开几个权限", 30, weight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp, bottom = 20.dp),
            )

            val active = listOf(perms.notify, perms.a11y).indexOfFirst { !it }.let { if (it == 1) 2 else it }

            Step(1, "允许通知", "用来点亮岛", Status.of(perms.notify), active == 0) {
                StepButton("允许", active == 0) {
                    if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        show(Guides.notify()) { Perms.openFirst(ctx, listOf(Perms.notificationSettingsIntent(ctx))) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            val liveStatus = when {
                !Rom.liveSupported -> Status.Unsupported
                perms.live -> Status.Done
                else -> Status.Todo
            }
            Step(
                2, "让提醒进${Rom.islandName}",
                when (liveStatus) {
                    Status.Unsupported -> "安卓 ${android.os.Build.VERSION.RELEASE} 还不支持，提醒会以普通通知出现"
                    else -> "打开「${Rom.liveSwitchName}」，有的手机不用开"
                },
                liveStatus, false, optional = true,
            ) {
                if (liveStatus == Status.Todo) {
                    StepButton("去打开", false) { show(Guides.live()) { Perms.openFirst(ctx, Perms.liveIntents(ctx)) } }
                }
            }
            Spacer(Modifier.height(10.dp))

            Step(
                3, "打开无障碍",
                when {
                    perms.a11yRecovering -> "vivo 暂停了它，几秒后自动恢复"
                    perms.a11yStuck -> "被系统停了，关掉再打开一次"
                    else -> "用来知道你在用哪个 App"
                },
                when {
                    perms.a11yOk -> Status.Done
                    perms.a11yRecovering -> Status.Recovering
                    else -> Status.Todo
                },
                active == 2 && !perms.a11yRecovering,
                extra = {
                    if (!perms.a11y) {
                        T(
                            "开关点不动？",
                            13, Palette.Accent, FontWeight.SemiBold,
                            Modifier
                                .padding(top = 10.dp)
                                .pressable { show(Guides.restricted()) { Perms.openFirst(ctx, listOf(Perms.appInfoIntent(ctx))) } },
                        )
                    }
                },
            ) {
                StepButton(if (perms.a11yStuck) "去重开" else "去打开", active == 2) {
                    val old = AppCatalog.isInstalled(ctx, "com.benben.focusisland")
                    show(Guides.accessibility(old)) {
                        Perms.a11yVisitAt = System.currentTimeMillis()
                        Perms.openFirst(ctx, listOf(Perms.accessibilityIntent()))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            val bgDone = visits >= 0 && Prefs.flag("bg") && Prefs.flag("auto")
            Step(
                4, "别让系统停掉它",
                if (Rom.vendor == Rom.Vendor.Vivo)
                    "不设的话会被后台冻住，提醒时灵时不灵。\n最近任务里点盯梢岛的名字，选「锁定」。"
                else
                    "不设的话会被系统停掉，提醒时灵时不灵。\n最近任务里把盯梢岛锁住。",
                if (bgDone) Status.Visited else Status.Todo, perms.ready && !bgDone,
            ) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepButton(if (Prefs.flag("bg")) "后台耗电 ✓" else "后台耗电", perms.ready && !Prefs.flag("bg")) {
                        show(Guides.background()) {
                            Prefs.setFlag("bg"); visits++
                            Perms.openFirst(ctx, Perms.backgroundIntents(ctx))
                        }
                    }
                    StepButton(if (Prefs.flag("auto")) "自启动 ✓" else "自启动", perms.ready && Prefs.flag("bg") && !Prefs.flag("auto")) {
                        show(Guides.autostart()) {
                            Prefs.setFlag("auto"); visits++
                            Perms.openFirst(ctx, Perms.autostartIntents(ctx))
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            Pill(
                if (perms.ready) "开始用" else "还差 ${perms.missing} 步",
                Modifier.fillMaxWidth(),
                enabled = perms.ready,
                height = 60.dp,
                onClick = onDone,
            )
            T(
                "自检",
                14, Palette.Sub, FontWeight.Medium,
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 16.dp)
                    .pressable(onClick = onCheck)
                    .padding(8.dp),
            )
            Spacer(Modifier.height(18.dp))
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Palette.Bg, Palette.Bg, Palette.Bg.copy(alpha = 0f))))
                .windowInsetsTopHeight(WindowInsets.statusBars)
        )
        GuideSheet(guide, onGo = go, onDismiss = { guide = null; pending = null })
    }
}

private enum class Status {
    Todo, Done, Visited, Unsupported, Recovering;

    companion object {
        fun of(ok: Boolean) = if (ok) Done else Todo
    }
}

@Composable
private fun StepButton(text: String, dark: Boolean, onClick: () -> Unit) {
    Pill(text, dark = dark, height = 38.dp, textSize = 14, onClick = onClick)
}

@Composable
private fun Step(
    n: Int,
    title: String,
    desc: String,
    status: Status,
    active: Boolean,
    optional: Boolean = false,
    extra: @Composable () -> Unit = {},
    action: @Composable () -> Unit,
) {
    val border by animateColorAsState(if (active) Palette.Ink else Color.Transparent, tween(240), label = "stepBorder")
    val done = status == Status.Done || status == Status.Visited
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Card)
            .border(1.5.dp, border, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AnimatedContent(
                done,
                transitionSpec = { scaleIn(spring(0.45f, 500f)) + fadeIn() togetherWith scaleOut() + fadeOut() },
                label = "stepDot",
            ) { d ->
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (d) Palette.Good else if (active) Palette.Ink else Palette.Faint),
                    contentAlignment = Alignment.Center,
                ) {
                    T(if (d) "✓" else "$n", 14, if (d || active) Color.White else Palette.Sub, FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f)) {
                T(title, 17, Palette.Ink, FontWeight.SemiBold)
                if (optional) T("可选", 12, Palette.Sub)
            }
            when (status) {
                Status.Done -> T("已开启", 14, Palette.Good, FontWeight.SemiBold)
                Status.Visited -> T("设过了", 14, Palette.Good, FontWeight.SemiBold)
                Status.Recovering -> T("正在恢复…", 14, Palette.Sub, FontWeight.SemiBold)
                Status.Unsupported -> T("不支持", 14, Palette.Sub, FontWeight.SemiBold)
                Status.Todo -> action()
            }
        }
        T(desc, 14, Palette.Sub, modifier = Modifier.padding(start = 44.dp, top = 6.dp), lineHeight = 20)
        if (status == Status.Visited) {
            Row(Modifier.padding(start = 44.dp, top = 10.dp)) { action() }
        }
        Box(Modifier.padding(start = 44.dp)) { extra() }
    }
}
