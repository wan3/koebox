package com.koebox.app.data

import org.json.JSONObject
import java.io.File

/** 一条字幕 */
data class Cue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

enum class SubFormat(val label: String) {
    LRC("LRC"),
    SRT("SRT"),
    VTT("VTT"),
    ASS("ASS"),
    NONE("无字幕");

    companion object {
        fun fromExt(ext: String): SubFormat = when (ext.lowercase()) {
            "lrc" -> LRC
            "srt" -> SRT
            "vtt" -> VTT
            "ass", "ssa" -> ASS
            else -> NONE
        }
    }
}

/** 专辑里的非音频文件：图片、字幕、文档 */
enum class AssetKind(val label: String) {
    IMAGE("图片"),
    SUBTITLE("字幕"),
    DOC("文档"),
    OTHER("其他")
}

data class Asset(
    val id: String,
    val album: String,
    val name: String,
    val path: String,
    val kind: String,
    val sizeBytes: Long = 0,
    val relDir: String = ""
) {
    val kindEnum: AssetKind
        get() = runCatching { AssetKind.valueOf(kind) }.getOrDefault(AssetKind.OTHER)

    val file: File get() = File(path)
    val exists: Boolean get() = file.exists()

    /** 能不能在应用内当文本读 */
    val isTextReadable: Boolean
        get() {
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in setOf(
                "txt", "md", "lrc", "srt", "vtt", "ass", "ssa", "sbv", "sub",
                "csv", "json", "xml", "html", "htm", "log", "ini", "yaml", "yml"
            )
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("album", album)
        put("name", name)
        put("path", path)
        put("kind", kind)
        put("sizeBytes", sizeBytes)
        put("relDir", relDir)
    }

    companion object {
        fun fromJson(o: JSONObject): Asset = Asset(
            id = o.optString("id"),
            album = o.optString("album"),
            name = o.optString("name"),
            path = o.optString("path"),
            kind = o.optString("kind", AssetKind.OTHER.name),
            sizeBytes = o.optLong("sizeBytes"),
            relDir = o.optString("relDir")
        )

        fun kindOf(name: String): AssetKind {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif" -> AssetKind.IMAGE
                "lrc", "srt", "vtt", "ass", "ssa", "sbv", "sub" -> AssetKind.SUBTITLE
                "txt", "md", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
                "epub", "html", "htm", "rtf", "csv", "json", "xml" -> AssetKind.DOC
                else -> AssetKind.OTHER
            }
        }
    }
}

/** 一个音轨（音声） */
data class Track(
    val id: String,
    val title: String,
    val audioPath: String,
    val coverPath: String?,
    val subPath: String?,
    val subFormat: SubFormat,
    val durationMs: Long,
    val lastPosMs: Long,
    val addedAt: Long,
    val folder: String = "",
    val sizeBytes: Long = 0,
    val relDir: String = "",
    val srcName: String = ""
) {
    val audioFile: File get() = File(audioPath)
    val coverFile: File? get() = coverPath?.let { File(it) }?.takeIf { it.exists() }
    val subFile: File? get() = subPath?.let { File(it) }?.takeIf { it.exists() }
    val hasSub: Boolean get() = subFile != null && subFormat != SubFormat.NONE

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("audioPath", audioPath)
        put("coverPath", coverPath ?: JSONObject.NULL)
        put("subPath", subPath ?: JSONObject.NULL)
        put("subFormat", subFormat.name)
        put("durationMs", durationMs)
        put("lastPosMs", lastPosMs)
        put("addedAt", addedAt)
        put("folder", folder)
        put("sizeBytes", sizeBytes)
        put("relDir", relDir)
        put("srcName", srcName)
    }

    companion object {
        fun fromJson(o: JSONObject): Track = Track(
            id = o.optString("id"),
            title = o.optString("title"),
            audioPath = o.optString("audioPath"),
            coverPath = if (o.isNull("coverPath")) null else o.optString("coverPath").ifEmpty { null },
            subPath = if (o.isNull("subPath")) null else o.optString("subPath").ifEmpty { null },
            subFormat = runCatching { SubFormat.valueOf(o.optString("subFormat")) }
                .getOrDefault(SubFormat.NONE),
            durationMs = o.optLong("durationMs"),
            lastPosMs = o.optLong("lastPosMs"),
            addedAt = o.optLong("addedAt"),
            folder = o.optString("folder"),
            sizeBytes = o.optLong("sizeBytes"),
            relDir = o.optString("relDir"),
            srcName = o.optString("srcName")
        )
    }
}

/** 导入结果反馈 */
data class ImportResult(
    val added: Int,
    val subtitleMatched: Int,
    val coverMatched: Int,
    val orphanSubs: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
    val missingCover: Int = 0,
    val assetsAdded: Int = 0,
    val skipped: List<String> = emptyList()
)

/** 一个文件夹（专辑） */
data class FolderGroup(
    val name: String,
    val tracks: List<Track>
) {
    val count: Int get() = tracks.size
    val coverPath: String? get() = tracks.firstOrNull { it.coverPath != null }?.coverPath
    val subCount: Int get() = tracks.count { it.hasSub }
    val totalMs: Long get() = tracks.sumOf { it.durationMs }
}

/** 去掉文件名开头的序号："Track01：xxx" / "01.xxx" / "03-xxx" → "xxx" */
fun cleanTitle(raw: String): String {
    val s = raw.trim()
    val patterns = listOf(
        Regex("""^(?i)track[\s_\-]?0*\d{1,3}\s*[：:._\-、]\s*"""),
        Regex("""^0*\d{1,3}\s*[：:._\-、]\s*""")
    )
    for (r in patterns) {
        val m = r.find(s)
        if (m != null && m.value.length < s.length) {
            val rest = s.substring(m.value.length).trim()
            if (rest.isNotEmpty()) return rest
        }
    }
    return s
}
