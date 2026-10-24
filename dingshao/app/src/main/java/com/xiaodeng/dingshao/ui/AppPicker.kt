package com.xiaodeng.dingshao.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaodeng.dingshao.AppCatalog
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 从底下弹上来的 App 选择面板，往下拖能收起 */
@Composable
fun AppPicker(
    visible: Boolean,
    apps: List<AppCatalog.Entry>,
    selected: Set<String>,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var sel by remember { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(visible) {
        if (visible) {
            sel = selected
            query = ""
        }
    }
    BackHandler(enabled = visible) { onDismiss() }

    AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(remember { MutableInteractionSource() }, null) { onDismiss() }
        )
    }

    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    LaunchedEffect(visible) { if (visible) drag.snapTo(0f) }

    AnimatedVisibility(
        visible,
        enter = slideInVertically(spring(0.86f, 380f)) { it },
        exit = slideOutVertically(tween(240)) { it },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .offset { IntOffset(0, drag.value.roundToInt()) }
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(Palette.Bg)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            ) {
                // 拖动区：把手 + 标题
                Column(
                    Modifier
                        .fillMaxWidth()
                        .draggable(
                            rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } },
                            Orientation.Vertical,
                            onDragStopped = { v ->
                                if (drag.value > with(density) { 140.dp.toPx() } || v > 1800f) onDismiss()
                                else scope.launch { drag.animateTo(0f, spring(0.7f, 500f)) }
                            },
                        )
                ) {
                    Box(
                        Modifier
                            .padding(top = 10.dp)
                            .align(Alignment.CenterHorizontally)
                            .size(40.dp, 5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD6D4CE))
                    )
                    Row(
                        Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        T("盯哪些 App", 24, weight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        T("已选 ${sel.size} 个", 14, Palette.Sub, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                SearchField(query) { query = it }

                val suggested = remember(apps) {
                    val order = AppCatalog.SUGGESTED.withIndex().associate { it.value to it.index }
                    apps.filter { it.pkg in order }.sortedBy { order[it.pkg] }
                }
                val rest = remember(apps, suggested) { apps - suggested.toSet() }
                val filtered = remember(query, apps) {
                    if (query.isBlank()) emptyList()
                    else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
                }
                val toggle = { pkg: String -> sel = if (pkg in sel) sel - pkg else sel + pkg }

                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    if (apps.isEmpty()) {
                        item { GroupLabel("读取中…") }
                    } else if (query.isBlank()) {
                        if (suggested.isNotEmpty()) {
                            item { GroupLabel("常刷的") }
                            items(suggested, key = { "s" + it.pkg }) { AppRow(it, it.pkg in sel) { toggle(it.pkg) } }
                        }
                        item { GroupLabel("全部") }
                        items(rest, key = { it.pkg }) { AppRow(it, it.pkg in sel) { toggle(it.pkg) } }
                    } else {
                        if (filtered.isEmpty()) item { GroupLabel("没找到「${query.trim()}」") }
                        items(filtered, key = { "q" + it.pkg }) { AppRow(it, it.pkg in sel) { toggle(it.pkg) } }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
                Pill(
                    if (sel.isEmpty()) "至少选一个" else "好了 · 盯 ${sel.size} 个",
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    enabled = sel.isNotEmpty(),
                ) { onDone(sel) }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 6.dp)
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Faint)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, color = Palette.Ink),
            cursorBrush = SolidColor(Palette.Accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) T("搜 App 名字", 16, Palette.Sub)
                inner()
            },
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    T(text, 13, Palette.Sub, FontWeight.Medium, Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
private fun AppRow(e: AppCatalog.Entry, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pressable(scaleTo = 0.98f, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(e, 44)
        T(e.label, 16, modifier = Modifier.padding(start = 14.dp).weight(1f), maxLines = 1)
        Check(checked)
    }
}

@Composable
fun AppIcon(e: AppCatalog.Entry, sizeDp: Int) {
    val img = remember(e.pkg) { e.icon?.asImageBitmap() }
    if (img != null) {
        Image(img, null, Modifier.size(sizeDp.dp).clip(RoundedCornerShape((sizeDp * 0.28f).dp)))
    } else {
        Box(Modifier.size(sizeDp.dp).clip(RoundedCornerShape((sizeDp * 0.28f).dp)).background(Palette.Faint))
    }
}

@Composable
private fun Check(on: Boolean) {
    val bg by animateColorAsState(if (on) Palette.Ink else Color.Transparent, tween(160), label = "chk")
    val s by animateFloatAsState(if (on) 1f else 0f, spring(0.45f, 600f), label = "chkS")
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, if (on) Palette.Ink else Color(0xFFCFCDC6), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        T("✓", 15, Color.White, FontWeight.Bold, Modifier.scale(s))
    }
}
