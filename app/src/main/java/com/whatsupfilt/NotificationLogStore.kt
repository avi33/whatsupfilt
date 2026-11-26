package com.whatsupfilt

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Keeps the last [MAX] captured notifications in memory and in a local JSON-lines file. */
object NotificationLogStore {
    private const val MAX = 300
    private const val FILE = "wa_notifications.jsonl"

    private val _entries = MutableStateFlow<List<LoggedNotification>>(emptyList())
    val entries: StateFlow<List<LoggedNotification>> = _entries

    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected: StateFlow<Boolean> = _listenerConnected

    @Volatile private var loaded = false

    fun setConnected(c: Boolean) { _listenerConnected.value = c }

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val f = file(ctx)
        if (!f.exists()) return
        _entries.value = f.readLines().mapNotNull { line ->
            runCatching { LoggedNotification.fromJson(JSONObject(line)) }.getOrNull()
        }.takeLast(MAX).reversed()
    }

    @Synchronized
    fun append(ctx: Context, e: LoggedNotification) {
        load(ctx)
        val list = (listOf(e) + _entries.value).take(MAX)
        _entries.value = list
        // Rewrite oldest-first so the file stays bounded.
        file(ctx).writeText(list.asReversed().joinToString("\n") { it.toJson().toString() } + "\n")
    }

    @Synchronized
    fun clear(ctx: Context) {
        _entries.value = emptyList()
        file(ctx).delete()
    }

    fun export(redact: Boolean): String =
        JSONArray().apply { _entries.value.forEach { put(it.toJson(redact)) } }.toString(2)

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)
}
