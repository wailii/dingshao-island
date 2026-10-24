package com.xiaodeng.dingshao.ui

import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaodeng.dingshao.AppCatalog
import com.xiaodeng.dingshao.BuildConfig
import com.xiaodeng.dingshao.Copy
import com.xiaodeng.dingshao.Engine
import com.xiaodeng.dingshao.Journal
import com.xiaodeng.dingshao.Perms
import com.xiaodeng.dingshao.Prefs
import com.xiaodeng.dingshao.Rom
import com.xiaodeng.dingshao.Xuexi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    perms: Perms.State,
    onOpenSetup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLog: () -> Unit,
    onRealPreview: () -> Unit,
) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val task by Prefs.task.collectAsState()
    val watched by Prefs.watched.collectAsState()
    val threshold by Prefs.thresholdSec.collectAsState()
    val monitoring by Prefs.monitoring.collectAsState()
    val since by Prefs.monitoringSince.collectAsState()
    val stats by Prefs.stats.collectAsState()
    val expandSec by Prefs.expandSec.collectAsState()
    val closeSec by Prefs.closeSec.collectAsState()
    val dailySec by Prefs.dailySec.collectAsState()
    val usedToday by Prefs.usedToday.collectAsState()
    val xs by Xuexi.status.collectAsState()
    val study = xs?.takeIf { it.date == LocalDate.now().toString() }
    val linked = remember { Xuexi.installed() }

    var apps by remember { mutableStateOf<List<AppCatalog.Entry>>(emptyList()) }
    var picker by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Prefs.watchedUnset) Prefs.setWatched(AppCatalog.defaultWatched(ctx))
        Rules.sanitize()
        apps = withContext(Dispatchers.IO) { AppCatalog.loadAll(ctx) }
    }
    LaunchedEffect(monitoring) { expanded = false }
    // 装了学习、还没写要专注做什么：替你填上「学：大模型」，只填一回
    LaunchedEffect(study?.nextCat) {
        val cat = study?.nextCat ?: return@LaunchedEffect
        if (Prefs.task.value.isBlank() && !Prefs.flag("task_auto")) {
            Prefs.setTask("学：$cat".take(20))
            Prefs.setFlag("task_auto")
        }
    }

    val watchedApps = remember(watched) { AppCatalog.entries(ctx, watched) }
    val sample = watchedApps.firstOrNull()
    val taskOrNull = task.trim().ifEmpty { null }
    val sampleName = sample?.label ?: "小红书"

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp)
                .padding(bottom = 132.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SecretLogo() }
                val devLog by Prefs.devLog.collectAsState()
                AnimatedVisibility(devLog, enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut() + scaleOut(targetScale = 0.6f)) {
                    Row {
                        TopChip("日志", false, onOpenLog)
                        Spacer(Modifier.width(8.dp))
                    }
                }
                TopChip("设置", (!perms.ready || perms.a11yStuck) && !perms.a11yRecovering, onOpenSettings)
            }
            Spacer(Modifier.height(34.dp))

            IslandHero(
                monitoring = monitoring,
                since = since,
                expanded = expanded,
                onTap = { expanded = !expanded },
                sample = sample,
                title = Copy.catchTitle(threshold.toLong(), threshold, false, sampleName, taskOrNull, 0L),
                subtitle = Copy.catchText(sampleName, taskOrNull),
            )

            HeroCaption(monitoring, expanded, threshold, if (dailySec > 0) dailySec + (study?.done ?: 0) * Xuexi.BONUS_SEC else dailySec, onRealPreview)

            AnimatedVisibility(
                !perms.ready || perms.a11yStuck || (!perms.live && Rom.liveSupported),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                SetupBanner(perms, monitoring, onOpenSetup)
            }

            Spacer(Modifier.height(14.dp))
            Panel {
                Label("要专注做什么")
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = task,
                    onValueChange = { Prefs.setTask(it.take(20)) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink),
                    cursorBrush = SolidColor(Palette.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (task.isEmpty()) T("比如：读完论文", 24, Color(0xFFC9C7C0), FontWeight.SemiBold)
                        inner()
                    },
                )
                // 学习里正在学的课，点一下填进来
                val hint = study?.nextCat?.let { "学：$it".take(20) }
                AnimatedVisibility(hint != null && hint != task, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    T(
                        hint ?: "", 13, Palette.Good, FontWeight.Medium,
                        Modifier
                            .padding(top = 10.dp)
                            .pressable { Prefs.setTask(hint ?: "") }
                            .clip(RoundedCornerShape(50))
                            .background(Palette.Good.copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }

            if (linked) {
                Spacer(Modifier.height(12.dp))
                StudyCard(study)
            }

            Spacer(Modifier.height(12.dp))
            Panel(Modifier.animateContentSize(spring(0.8f, 400f))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("盯这些 App", Modifier.weight(1f))
                    T("改", 14, Palette.Ink, FontWeight.SemiBold, Modifier.pressable { picker = true }.padding(4.dp))
                }
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    watchedApps.forEach { e ->
                        Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(e, 48)
                            T(e.label, 11, Palette.Sub, maxLines = 1, modifier = Modifier.padding(top = 6.dp), align = TextAlign.Center)
                        }
                    }
                    Column(Modifier.width(58.dp).pressable { picker = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(Palette.Faint),
                            contentAlignment = Alignment.Center,
                        ) { T("+", 24, Palette.Sub, FontWeight.Light) }
                        T("添加", 11, Palette.Sub, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            val (rule1, rule2) = Rules.summary(threshold, dailySec, expandSec, closeSec)
            Panel(Modifier.pressable(scaleTo = 0.98f, onClick = onOpenSettings)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Label("规则")
                        T(rule1, 18, Palette.Ink, FontWeight.SemiBold, Modifier.padding(top = 6.dp))
                        T(rule2, 13, Palette.Sub, modifier = Modifier.padding(top = 2.dp))
                    }
                    T("›", 22, Palette.Sub)
                }
            }
            Spacer(Modifier.height(12.dp))
            StatsPanel(stats, usedToday, dailySec, study)
        }

        // 状态栏底下垫一层底色，内容滚上去不会压到时间和电量
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Palette.Bg, Palette.Bg, Palette.Bg.copy(alpha = 0f))))
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .padding(bottom = 0.dp)
        )

        BottomBar(
            Modifier.align(Alignment.BottomCenter),
            perms, monitoring, watched.isEmpty(),
            onSetup = onOpenSetup,
            onPick = { picker = true },
        )

        AppPicker(
            visible = picker,
            apps = apps,
            selected = watched,
            onDone = {
                Prefs.setWatched(it)
                Engine.onRulesChanged()
                picker = false
            },
            onDismiss = { picker = false },
        )

    }
}

/** 左上角的「盯梢岛」：连点 10 次打开日志入口，再连点 10 次隐藏；点到第 7 次开始提示还差几次 */
@Composable
private fun SecretLogo() {
    val ctx = LocalContext.current
    val view = LocalView.current
    var taps by remember { mutableStateOf(0) }
    var last by remember { mutableStateOf(0L) }
    var toast by remember { mutableStateOf<Toast?>(null) }
    T(
        "盯梢岛", 26, weight = FontWeight.Bold,
        modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) {
            val now = System.currentTimeMillis()
            taps = if (now - last < 1200) taps + 1 else 1
            last = now
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            val on = Prefs.devLog.value
            val msg = when {
                taps >= 10 -> {
                    taps = 0
                    Prefs.setFlag("devlog", !on)
                    Journal.add(Journal.Kind.Rule, if (on) "连点 10 次，隐藏了日志入口" else "连点 10 次，打开了日志入口")
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    if (on) "日志入口已隐藏" else "日志入口已打开"
                }
                taps >= 7 -> "再点 ${10 - taps} 次${if (on) "隐藏" else "打开"}日志"
                else -> null
            }
            if (msg != null) {
                toast?.cancel()
                toast = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).also { it.show() }
            }
        },
    )
}

@Composable
private fun TopChip(text: String, dot: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(Palette.Card)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Palette.Accent))
            Spacer(Modifier.width(6.dp))
        }
        T(text, 14, Palette.Ink, FontWeight.Medium)
    }
}

@Composable
private fun HeroCaption(
    monitoring: Boolean,
    expanded: Boolean,
    single: Int,
    daily: Int,
    onRealPreview: () -> Unit,
) {
    val key = when {
        expanded -> 2
        monitoring -> 1
        else -> 0
    }
    AnimatedContent(
        key,
        transitionSpec = {
            (fadeIn(tween(240, 80)) + slideInVertically(tween(240, 80)) { it / 3 }) togetherWith fadeOut(tween(120))
        },
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        label = "caption",
    ) { k ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            when (k) {
                2 -> {
                    T("到点时${Rom.islandName}会这样展开", 14, Palette.Sub, align = TextAlign.Center)
                    Pill(
                        "在${Rom.islandName}上试一下", Modifier.padding(top = 12.dp), dark = false, height = 40.dp, textSize = 14,
                        onClick = onRealPreview,
                    )
                }

                1 -> T(
                    "单次满 ${Copy.duration(single.toLong())}" +
                        (if (daily > 0) "，或今天满 ${Copy.duration(daily.toLong())}" else "") + "，岛就亮",
                    14, Palette.Sub, align = TextAlign.Center,
                )

                else -> T("点小岛预览提醒", 14, Palette.Sub, align = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SetupBanner(perms: Perms.State, monitoring: Boolean, onClick: () -> Unit) {
    // 按真实缺的那一项说，别张冠李戴
    val (head, sub, action) = when {
        perms.a11yRecovering -> Triple("无障碍恢复中…", "", "")
        !perms.a11y -> Triple("无障碍关了，盯不到", "划掉盯梢岛也会关掉它", "去打开")
        perms.a11yStuck -> Triple("无障碍断开了", "关掉再打开一次", "去打开")
        !perms.notify -> Triple("通知没开", "", "去打开")
        else -> Triple(
            "提醒进不了${Rom.islandName}",
            if (Rom.vendor == Rom.Vendor.Vivo) "更新后要重新打开「显示实时活动」" else "",
            "去打开",
        )
    }
    Row(
        Modifier
            .padding(top = 22.dp)
            .fillMaxWidth()
            .pressable(scaleTo = 0.98f, onClick = onClick)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFFFEDE4))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T(head, 16, Palette.Ink, FontWeight.SemiBold)
            if (sub.isNotEmpty()) T(sub, 13, Color(0xFFB0573A), modifier = Modifier.padding(top = 2.dp), lineHeight = 19)
        }
        Spacer(Modifier.width(12.dp))
        T(action, 14, Palette.Accent, FontWeight.SemiBold)
    }
}

/** 学习那边今天的课：还剩几节、下一节，点「去学一节」直接开课 */
@Composable
private fun StudyCard(study: Xuexi.Status?) {
    val ctx = LocalContext.current
    val left = study?.left ?: 0
    val (head, sub, btn) = when {
        study == null -> Triple("学习", "打开看看今天的课", "打开")
        left > 0 -> Triple("还剩 $left 节", study.nextText ?: "", "去学一节")
        else -> Triple("今天学完了", "学了 ${study.done} 节", "再学一节")
    }
    Panel(Modifier.pressable(scaleTo = 0.98f) { Xuexi.open(ctx, "island") }) {
        Label("学习")
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedContent(
                        head,
                        transitionSpec = {
                            (slideInVertically(spring(0.7f, 400f)) { it } + fadeIn()) togetherWith
                                (slideOutVertically(tween(160)) { -it } + fadeOut(tween(160)))
                        },
                        label = "studyHead",
                    ) { T(it, 22, Palette.Ink, FontWeight.Bold) }
                    if (left > 0) T("约 ${study?.min} 分钟", 13, Palette.Sub, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                }
                if (sub.isNotEmpty()) T(sub, 13, Palette.Sub, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.width(12.dp))
            Pill(btn, dark = left > 0 || study == null, height = 40.dp, textSize = 14) { Xuexi.open(ctx, "island") }
        }
    }
}

@Composable
private fun StatsPanel(stats: Prefs.Stats, usedSec: Long, dailySec: Int, study: Xuexi.Status?) {
    val bonus = (study?.done ?: 0) * Xuexi.BONUS_SEC
    val limit = if (dailySec > 0) dailySec + bonus else dailySec
    Panel {
        Label("今天")
        Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                if (study != null) T("刷了", 12, Palette.Sub)
                Row(verticalAlignment = Alignment.Bottom) {
                    T(Copy.duration(usedSec), 30, Palette.Ink, FontWeight.Bold, tabular = true)
                    T(if (limit > 0) " / ${Copy.duration(limit.toLong())}" else " 在这些 App 里", 14, Palette.Sub, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp))
                }
            }
            if (study != null) {
                Column(horizontalAlignment = Alignment.End) {
                    T("学了", 12, Palette.Good)
                    T(Copy.duration(study.studySec), 30, Palette.Good, FontWeight.Bold, tabular = true)
                }
            }
        }
        if (limit > 0) {
            val p = (usedSec.toFloat() / limit).coerceIn(0f, 1f)
            // 学来的那截额度接在最后，浅绿；学完一节它弹着变长
            val b by animateFloatAsState(bonus.toFloat() / limit, spring(0.5f, 120f), label = "bonus")
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Palette.Faint)
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxWidth(b)
                        .height(6.dp)
                        .background(Palette.Good.copy(alpha = 0.3f))
                )
                Box(
                    Modifier
                        .fillMaxWidth(p)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (p >= 1f) Palette.Accent else Palette.Ink)
                )
            }
        }
        if (bonus > 0) BonusLine(study?.done ?: 0, bonus)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("被抓到", stats.caught, Modifier.weight(1f))
            Stat("自己回来", stats.returned, Modifier.weight(1f))
            Stat("又赖 5 分钟", stats.snoozed, Modifier.weight(1f))
            Stat("被退回桌面", stats.kicked, Modifier.weight(1f))
        }
    }
}

/** 「学完 2 节，每天多给 20 分钟」；刚学完一节时，飘起一个「+10 分钟」 */
@Composable
private fun BonusLine(done: Int, bonusSec: Int) {
    var last by remember { mutableStateOf(done) }
    val rise = remember { Animatable(1f) }
    LaunchedEffect(done) {
        if (done > last) {
            rise.snapTo(0f)
            rise.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
        }
        last = done
    }
    Box(Modifier.fillMaxWidth()) {
        T("学完 $done 节，每天多给 ${Copy.duration(bonusSec.toLong())}", 13, Palette.Good, modifier = Modifier.padding(top = 8.dp))
        if (rise.value < 1f) {
            T(
                "+10 分钟", 16, Palette.Good, FontWeight.Bold,
                Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        val k = rise.value
                        translationY = -56f * k
                        alpha = if (k < 0.15f) k / 0.15f else 1f - (k - 0.15f) / 0.85f
                        scaleX = 0.7f + 0.5f * minOf(1f, k * 4)
                        scaleY = scaleX
                    },
            )
        }
    }
}

@Composable
private fun Stat(label: String, n: Int, modifier: Modifier) {
    Column(modifier) {
        AnimatedContent(
            n,
            transitionSpec = {
                (slideInVertically(spring(0.7f, 400f)) { it } + fadeIn()) togetherWith
                    (slideOutVertically(tween(160)) { -it } + fadeOut(tween(160)))
            },
            label = "stat",
        ) { v -> T("$v", 22, Palette.Ink, FontWeight.Bold, tabular = true) }
        T(label, 12, Palette.Sub)
    }
}

@Composable
private fun BottomBar(
    modifier: Modifier,
    perms: Perms.State,
    monitoring: Boolean,
    noApps: Boolean,
    onSetup: () -> Unit,
    onPick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Palette.Bg.copy(alpha = 0f), Palette.Bg, Palette.Bg)))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 14.dp)
    ) {
        val label = when {
            !perms.ready && !monitoring -> "还差 ${perms.missing} 个权限"
            noApps && !monitoring -> "先选要盯的 App"
            monitoring -> "结束盯梢"
            else -> "开始盯梢"
        }
        Pill(
            label,
            Modifier.fillMaxWidth(),
            dark = !monitoring,
            height = 60.dp,
            haptic = if (!monitoring) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT,
        ) {
            when {
                monitoring -> Engine.stopMonitoring()
                !perms.ready -> onSetup()
                noApps -> onPick()
                else -> Engine.startMonitoring()
            }
        }
    }
}
