package com.xiaodeng.dingshao

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 日志：看到了什么、做了什么、系统怎么回应的、哪里出了错。
 * 存在本机文件里，只留最近 3000 条，不上传；用户自己复制或分享出去。
 */
object Journal {

    enum class Kind { App, Island, Service, Rule, Error, Device, Window }

    data class Entry(val t: Long, val kind: Kind, val text: String)

    private const val MAX = 3000
    private val io = Executors.newSingleThreadExecutor()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries
    private var file: File? = null
    private var appCtx: Context? = null
    private var appended = 0

    fun init(ctx: Context) {
        if (file != null) return
        appCtx = ctx.applicationContext
        val f = File(ctx.filesDir, "journal.tsv")
        file = f
        _entries.value = runCatching {
            if (!f.exists()) emptyList()
            else f.readLines().takeLast(MAX).mapNotNull { line ->
                val p = line.split('\t', limit = 3)
                if (p.size < 3) null
                else Entry(p[0].toLongOrNull() ?: return@mapNotNull null, runCatching { Kind.valueOf(p[1]) }.getOrDefault(Kind.Rule), p[2])
            }
        }.getOrDefault(emptyList())
        installCrashHook()
    }

    fun add(kind: Kind, text: String) {
        val e = Entry(System.currentTimeMillis(), kind, text.replace('\t', ' ').replace("\n", " / "))
        // 同时打到系统日志，连着电脑时 adb logcat -s Dingshao 就能看
        android.util.Log.i("Dingshao", "[$kind] ${e.text}")
        val next = (_entries.value + e).takeLast(MAX)
        _entries.value = next
        val f = file ?: return
        io.execute {
            runCatching {
                f.appendText("${e.t}\t${e.kind}\t${e.text}\n")
                // 文件太长时按内存里的最近 3000 条整体重写一次
                if (++appended % 200 == 0 && f.length() > 600_000) {
                    f.writeText(_entries.value.joinToString("\n", postfix = "\n") { "${it.t}\t${it.kind}\t${it.text}" })
                }
            }
        }
    }

    /** 出错不再悄悄吞掉：记下在哪、什么错、出错的那一行 */
    fun error(where: String, t: Throwable) {
        val at = t.stackTrace.firstOrNull { it.className.startsWith("com.xiaodeng") } ?: t.stackTrace.firstOrNull()
        add(Kind.Error, "$where 出错：${t.javaClass.simpleName}: ${t.message ?: ""}${at?.let { " @${it.fileName}:${it.lineNumber}" } ?: ""}")
    }

    /** 崩溃前写一笔，同步写盘，免得进程没了还没落下来 */
    private fun installCrashHook() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, ex ->
            runCatching {
                val trace = ex.stackTrace.take(6).joinToString(" < ") { "${it.fileName}:${it.lineNumber}" }
                val line = "${System.currentTimeMillis()}\t${Kind.Error}\t崩溃（${th.name}）：${ex.javaClass.simpleName}: ${ex.message ?: ""} · $trace\n"
                file?.appendText(line)
            }
            prev?.uncaughtException(th, ex)
        }
    }

    fun today(): List<Entry> {
        val start = startOfToday()
        return _entries.value.filter { it.t >= start }
    }

    /** 给开发者的完整版本：现场快照 + 进程退出原因 + 全部日志 */
    fun export(ctx: Context): String {
        val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
        return buildString {
            append("盯梢岛日志 · 导出于 ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())}\n\n")
            append("【机型】\n").append(Diag.device(ctx)).append('\n')
            append("【现在的状态】\n").append(Diag.state(ctx)).append('\n')
            append("【进程最近几次是怎么结束的】\n")
            Diag.exits(ctx).ifEmpty { listOf("（系统没留记录）") }.forEach { append("  ").append(it).append('\n') }
            append("\n【日志】共 ${_entries.value.size} 条\n")
            _entries.value.forEach { append("${fmt.format(Date(it.t))} [${tag(it.kind)}] ${it.text}\n") }
        }
    }

    /** 写成文件，给「分享日志文件」用 */
    fun exportFile(ctx: Context): File {
        val dir = File(ctx.cacheDir, "logs").apply { mkdirs() }
        val name = "盯梢岛日志-${android.os.Build.BRAND}-${android.os.Build.MODEL}-${SimpleDateFormat("MMdd-HHmm", Locale.CHINA).format(Date())}.txt"
            .replace(Regex("[\\\\/:*?\"<>| ]"), "_")
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, name).apply { writeText(export(ctx)) }
    }

    fun tag(k: Kind) = when (k) {
        Kind.App -> "应用"
        Kind.Island -> "岛"
        Kind.Service -> "服务"
        Kind.Rule -> "设置"
        Kind.Error -> "出错"
        Kind.Device -> "设备"
        Kind.Window -> "窗口"
    }

    private fun startOfToday(): Long {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.HOUR_OF_DAY, 0)
        c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
