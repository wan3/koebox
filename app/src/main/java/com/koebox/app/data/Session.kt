package com.koebox.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** 一次收听记录：按专辑归档 */
data class ListeningSession(
    val id: String,
    val album: String,
    val startAt: Long,
    val endAt: Long,
    val durationMs: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("album", album)
        put("startAt", startAt)
        put("endAt", endAt)
        put("durationMs", durationMs)
    }

    companion object {
        fun fromJson(o: JSONObject): ListeningSession = ListeningSession(
            id = o.optString("id", UUID.randomUUID().toString()),
            album = o.optString("album"),
            startAt = o.optLong("startAt"),
            endAt = o.optLong("endAt"),
            durationMs = o.optLong("durationMs")
        )
    }
}

/** 收听记录存储，只保留最近 7 天 */
class SessionRepo(ctx: Context) {

    private val file = File(ctx.filesDir, "sessions.json")
    private val keepMs = 7L * 24 * 60 * 60 * 1000

    fun load(): MutableList<ListeningSession> {
        val out = mutableListOf<ListeningSession>()
        if (!file.exists()) return out
        try {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) out.add(ListeningSession.fromJson(arr.getJSONObject(i)))
        } catch (_: Exception) {
        }
        val cutoff = System.currentTimeMillis() - keepMs
        return out.filter { it.endAt >= cutoff }.sortedByDescending { it.startAt }.toMutableList()
    }

    fun save(list: List<ListeningSession>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            file.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    /** 追加一条并清理超过 7 天的旧记录 */
    fun append(list: MutableList<ListeningSession>, item: ListeningSession): MutableList<ListeningSession> {
        val cutoff = System.currentTimeMillis() - keepMs
        list.add(0, item)
        val kept = list.filter { it.endAt >= cutoff }.sortedByDescending { it.startAt }.toMutableList()
        save(kept)
        return kept
    }
}
