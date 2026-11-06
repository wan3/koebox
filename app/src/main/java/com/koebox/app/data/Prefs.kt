package com.koebox.app.data

import android.content.Context

/** 轻量设置存储 */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("koebox", Context.MODE_PRIVATE)

    var subOffsetMs: Long
        get() = sp.getLong("sub_offset", 0L)
        set(v) = sp.edit().putLong("sub_offset", v).apply()

    var subFontSp: Float
        get() = sp.getFloat("sub_font", 19f)
        set(v) = sp.edit().putFloat("sub_font", v).apply()

    var speed: Float
        get() = sp.getFloat("speed", 1f)
        set(v) = sp.edit().putFloat("speed", v).apply()

    var showSecondLine: Boolean
        get() = sp.getBoolean("show_next", true)
        set(v) = sp.edit().putBoolean("show_next", v).apply()

    var subtitleOnTop: Boolean
        get() = sp.getBoolean("sub_top", false)
        set(v) = sp.edit().putBoolean("sub_top", v).apply()

    var lastTrackId: String?
        get() = sp.getString("last_track", null)
        set(v) = sp.edit().putString("last_track", v).apply()

    /** 悬浮字幕：播放时在其它页面浮一条字幕 */
    var floatingSub: Boolean
        get() = sp.getBoolean("float_sub", false)
        set(v) = sp.edit().putBoolean("float_sub", v).apply()
}

/** 时间格式化 */
object Fmt {
    fun time(ms: Long): String {
        if (ms <= 0) return "0:00"
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }

    fun short(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val m = total / 60
        val s = total % 60
        return String.format("%d:%02d", m, s)
    }

    /** 进度条用 */
    val timeFmt: (Long) -> String = { time(it) }
}
