package com.koebox.app.data

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min

/** 极简 ID3v2 解析：只为拿到内嵌封面 APIC / 标题 / 艺人 */
object Id3 {

    data class Meta(
        val title: String?,
        val artist: String?,
        val picture: ByteArray?,
        val pictureMime: String?
    )

    private const val MAX_TAG = 12 * 1024 * 1024

    fun read(file: File): Meta? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val head = ByteArray(10)
                if (raf.read(head) < 10) return null
                if (head[0] != 'I'.code.toByte() || head[1] != 'D'.code.toByte() || head[2] != '3'.code.toByte()) {
                    return null
                }
                val major = head[3].toInt() and 0xFF
                if (major < 2 || major > 4) return null
                val size = synchsafe(head, 6)
                if (size <= 0) return null
                val buf = ByteArray(min(size, MAX_TAG))
                var read = 0
                while (read < buf.size) {
                    val n = raf.read(buf, read, buf.size - read)
                    if (n <= 0) break
                    read += n
                }
                parseFrames(buf, read, major)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun synchsafe(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0x7F) shl 21) or
            ((b[off + 1].toInt() and 0x7F) shl 14) or
            ((b[off + 2].toInt() and 0x7F) shl 7) or
            (b[off + 3].toInt() and 0x7F)

    private fun be32(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)

    private fun parseFrames(b: ByteArray, len: Int, major: Int): Meta? {
        var idLen = 4
        var headerLen = 10
        if (major == 2) {
            idLen = 3
            headerLen = 6
        }
        var title: String? = null
        var artist: String? = null
        var pic: ByteArray? = null
        var picMime: String? = null

        var p = 0
        while (p + headerLen <= len) {
            val id = String(b, p, idLen, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            var frameSize = if (major == 2) {
                ((b[p + 3].toInt() and 0xFF) shl 16) or
                    ((b[p + 4].toInt() and 0xFF) shl 8) or
                    (b[p + 5].toInt() and 0xFF)
            } else if (major == 4) {
                synchsafe(b, p + 4)
            } else {
                be32(b, p + 4)
            }
            if (frameSize <= 0 || p + headerLen + frameSize > len) break

            val dataStart = p + headerLen
            val dataEnd = dataStart + frameSize

            when (id) {
                "TIT2", "TT2" -> if (title == null) title = decodeText(b, dataStart, dataEnd)
                "TPE1", "TP1" -> if (artist == null) artist = decodeText(b, dataStart, dataEnd)
                "APIC", "PIC" -> if (pic == null) {
                    val res = decodeApic(b, dataStart, dataEnd, major == 2)
                    if (res != null) {
                        pic = res.first
                        picMime = res.second
                    }
                }
            }

            p = dataEnd
        }
        if (title.isNullOrBlank() && artist.isNullOrBlank() && pic == null) return null
        return Meta(title?.trim()?.ifBlank { null }, artist?.trim()?.ifBlank { null }, pic, picMime)
    }

    private fun decodeText(b: ByteArray, start: Int, end: Int): String? {
        if (end - start < 2) return null
        val enc = b[start].toInt() and 0xFF
        return try {
            decodeString(b, start + 1, end, enc)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeApic(b: ByteArray, start: Int, end: Int, v22: Boolean): Pair<ByteArray, String>? {
        if (end - start < 6) return null
        var p = start
        val enc = b[p].toInt() and 0xFF
        p++
        var mime: String
        if (v22) {
            mime = String(b, p, 3, Charsets.ISO_8859_1).trim()
            p += 3
            if (mime.equals("JPG", true)) mime = "image/jpeg"
            if (mime.equals("PNG", true)) mime = "image/png"
        } else {
            val ms = p
            while (p < end && b[p] != 0.toByte()) p++
            mime = String(b, ms, p - ms, Charsets.ISO_8859_1)
            p++
        }
        if (p >= end) return null
        p++ // picture type
        // description
        if (enc == 1 || enc == 2) {
            while (p + 1 < end && !(b[p] == 0.toByte() && b[p + 1] == 0.toByte())) p += 2
            p += 2
        } else {
            while (p < end && b[p] != 0.toByte()) p++
            p++
        }
        if (p >= end) return null
        val data = b.copyOfRange(p, end)
        if (data.size < 100) return null
        return Pair(data, mime.ifBlank { "image/jpeg" })
    }

    private fun decodeString(b: ByteArray, start: Int, end: Int, enc: Int): String {
        val len = end - start
        if (len <= 0) return ""
        return when (enc) {
            1 -> {
                if (len >= 2 && b[start] == 0xFF.toByte() && b[start + 1] == 0xFE.toByte()) {
                    String(b, start + 2, len - 2, Charsets.UTF_16LE)
                } else if (len >= 2 && b[start] == 0xFE.toByte() && b[start + 1] == 0xFF.toByte()) {
                    String(b, start + 2, len - 2, Charsets.UTF_16BE)
                } else {
                    String(b, start, len, Charsets.UTF_16LE)
                }
            }
            2 -> String(b, start, len, Charsets.UTF_16BE)
            3 -> String(b, start, len, Charsets.UTF_8)
            else -> String(b, start, len, Charsets.ISO_8859_1)
        }.trimEnd('\u0000').trim()
    }
}
