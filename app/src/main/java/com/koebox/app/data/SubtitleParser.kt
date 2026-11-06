package com.koebox.app.data

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 字幕解析：LRC / SRT / VTT / ASS(SSA)
 * 会自动处理 UTF-8(BOM) / UTF-16 / Shift_JIS 编码，日文字幕常见 SJIS 也能读。
 */
object SubtitleParser {

    fun parse(file: File, format: SubFormat): List<Cue> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        return try {
            val raw = readText(file)
            val cues = when (format) {
                SubFormat.LRC -> parseLrc(raw)
                SubFormat.SRT, SubFormat.VTT -> parseSrtLike(raw)
                SubFormat.ASS -> parseAss(raw)
                SubFormat.NONE -> detectAndParse(raw)
            }
            normalize(cues)
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun detectAndParse(raw: String): List<Cue> {
        if (raw.contains("-->")) return parseSrtLike(raw)
        if (Regex("""\[\d{1,2}:\d{1,2}""").containsMatchIn(raw)) return parseLrc(raw)
        return emptyList()
    }

    // ---------------- 编码 ----------------

    private fun readText(file: File): String = decodeBytes(file.readBytes())

    /** 按 BOM / 试解码自动判断编码，读不出来再退回 Shift_JIS */
    fun decodeBytes(b: ByteArray): String {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) {
            return String(b, 3, b.size - 3, Charsets.UTF_8)
        }
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) {
            return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        }
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) {
            return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(b)).toString()
        } catch (e: Exception) {
            try {
                String(b, Charset.forName("Shift_JIS"))
            } catch (e2: Exception) {
                String(b, Charsets.UTF_8)
            }
        }
    }

    // ---------------- LRC ----------------

    private val lrcTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]""")
    private val lrcOffset = Regex("""\[offset:\s*([+-]?\d+)\]""", RegexOption.IGNORE_CASE)

    private fun parseLrc(raw: String): List<Cue> {
        val globalOffset = lrcOffset.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val out = ArrayList<Cue>()
        raw.lineSequence().forEach { line ->
            val matches = lrcTag.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val last = matches.last()
            var text = line.substring(last.range.last + 1).trim()
            if (text.isEmpty()) return@forEach
            matches.forEach { m ->
                val min = m.groupValues[1].toLongOrNull() ?: return@forEach
                val sec = m.groupValues[2].toLongOrNull() ?: return@forEach
                val fracRaw = m.groupValues[3]
                val frac = when {
                    fracRaw.isEmpty() -> 0L
                    fracRaw.length == 1 -> fracRaw.toLong() * 100
                    fracRaw.length == 2 -> fracRaw.toLong() * 10
                    else -> fracRaw.take(3).toLong()
                }
                val start = min * 60_000 + sec * 1000 + frac - globalOffset
                out.add(Cue(start.coerceAtLeast(0), 0L, text))
            }
        }
        return out
    }

    // ---------------- SRT / VTT ----------------

    private val timeToken = Regex("""^(?:(\d{1,3}):)?(?:(\d{1,3}):)?(\d{1,2})(?:[.,](\d{1,3}))?""")
    private val bracketTag = Regex("""<[^>]*>""")
    private val braceTag = Regex("""\{[^}]*\}""")

    fun parseTimeMs(s: String): Long? {
        val t = s.trim()
        val m = timeToken.find(t) ?: return null
        val a = m.groupValues[1].toLongOrNull() ?: 0L
        val b = m.groupValues[2].toLongOrNull()
        val sec = m.groupValues[3].toLongOrNull() ?: return null
        val fracRaw = m.groupValues[4]
        val frac = when {
            fracRaw.isEmpty() -> 0L
            fracRaw.length == 1 -> fracRaw.toLong() * 100
            fracRaw.length == 2 -> fracRaw.toLong() * 10
            else -> fracRaw.take(3).toLong()
        }
        val (hours, minutes) = if (b == null) Pair(0L, a) else Pair(a, b)
        return ((hours * 60 + minutes) * 60 + sec) * 1000 + frac
    }

    private fun cleanText(s: String): String =
        s.replace(braceTag, "").replace(bracketTag, "").trim()

    private fun parseSrtLike(raw: String): List<Cue> {
        val norm = raw.replace("\r\n", "\n").replace('\r', '\n')
        val blocks = norm.split(Regex("""\n[ \t]*\n"""))
        val out = ArrayList<Cue>()
        for (block in blocks) {
            val lines = block.lines()
            val arrowIdx = lines.indexOfFirst { it.contains("-->") }
            if (arrowIdx < 0) continue
            val timeLine = lines[arrowIdx]
            val parts = timeLine.split("-->")
            if (parts.size < 2) continue
            val start = parseTimeMs(parts[0]) ?: continue
            val endRaw = parts[1].trim().split(Regex("""\s+""")).firstOrNull() ?: continue
            val end = parseTimeMs(endRaw) ?: (start + 3000)
            val text = lines.drop(arrowIdx + 1)
                .joinToString("\n") { cleanText(it) }
                .trim()
            if (text.isEmpty() || text == "WEBVTT") continue
            out.add(Cue(start, end, text))
        }
        return out
    }

    // ---------------- ASS ----------------

    private fun parseAss(raw: String): List<Cue> {
        val out = ArrayList<Cue>()
        raw.lineSequence().forEach { line ->
            val l = line.trim()
            if (!l.startsWith("Dialogue:", ignoreCase = true)) return@forEach
            val body = l.substringAfter(":").trim()
            val f = body.split(",")
            if (f.size < 10) return@forEach
            val start = parseTimeMs(f[1]) ?: return@forEach
            val end = parseTimeMs(f[2]) ?: return@forEach
            val text = f.drop(9).joinToString(",")
                .replace(braceTag, "")
                .replace("\\N", "\n")
                .replace("\\n", "\n")
                .replace("\\h", " ")
                .trim()
            if (text.isEmpty()) return@forEach
            out.add(Cue(start, end, text))
        }
        return out
    }

    // ---------------- 收尾 ----------------

    private fun normalize(cues: List<Cue>): List<Cue> {
        if (cues.isEmpty()) return emptyList()
        val sorted = cues.filter { it.text.isNotBlank() && it.startMs >= 0 }.sortedBy { it.startMs }
        val out = ArrayList<Cue>(sorted.size)
        for (i in sorted.indices) {
            val c = sorted[i]
            var end = c.endMs
            if (end <= c.startMs) {
                end = if (i + 1 < sorted.size) sorted[i + 1].startMs else c.startMs + 4000
            }
            if (end <= c.startMs) end = c.startMs + 1500
            out.add(c.copy(endMs = end))
        }
        return out
    }

    /** 二分查找当前时间对应的字幕下标，找不到返回 -1 */
    fun indexAt(cues: List<Cue>, positionMs: Long): Int {
        if (cues.isEmpty()) return -1
        var lo = 0
        var hi = cues.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (cues[mid].startMs <= positionMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        if (ans < 0) return -1
        return if (positionMs <= cues[ans].endMs + 700) ans else -1
    }

    /**
     * 保持式索引：取"最后一句已经开始的字幕"，不管它是否结束。
     * 音声字幕句与句之间常有几秒停顿，用 [indexAt] 会在停顿里显示空白，
     * 视觉上像"字幕不动了"；这里让上一句一直保留到下一句出现。
     * 还没播到第一句时返回 -1。
     */
    fun indexAtHold(cues: List<Cue>, positionMs: Long): Int {
        if (cues.isEmpty()) return -1
        var lo = 0
        var hi = cues.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (cues[mid].startMs <= positionMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }
}
