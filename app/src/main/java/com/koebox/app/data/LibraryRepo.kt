package com.koebox.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID

class LibraryRepo(private val ctx: Context) {

    private val tracksFile = File(ctx.filesDir, "library.json")
    private val assetsFile = File(ctx.filesDir, "assets.json")
    private val libDir = File(ctx.filesDir, "library").apply { mkdirs() }
    private val subDir = File(ctx.filesDir, "subs").apply { mkdirs() }
    private val assetDir = File(ctx.filesDir, "assets").apply { mkdirs() }

    val coverDir = File(ctx.filesDir, "covers").apply { mkdirs() }

    private val cueCache = HashMap<String, List<Cue>>()

    // ---------------- 存取 ----------------

    fun load(): MutableList<Track> {
        if (!tracksFile.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(tracksFile.readText())
            val out = mutableListOf<Track>()
            for (i in 0 until arr.length()) {
                val t = Track.fromJson(arr.getJSONObject(i))
                if (t.audioPath.isNotEmpty() && File(t.audioPath).exists()) {
                    val cleaned = cleanTitle(t.title)
                    out.add(if (cleaned != t.title) t.copy(title = cleaned) else t)
                }
            }
            out
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(list: List<Track>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            tracksFile.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    fun loadAssets(): MutableList<Asset> {
        if (!assetsFile.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(assetsFile.readText())
            val out = mutableListOf<Asset>()
            for (i in 0 until arr.length()) {
                val a = Asset.fromJson(arr.getJSONObject(i))
                if (a.path.isNotEmpty() && File(a.path).exists()) out.add(a)
            }
            out
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveAssets(list: List<Asset>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            assetsFile.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    fun subtitleOf(track: Track): List<Cue> {
        val path = track.subPath ?: return emptyList()
        cueCache[path]?.let { return it }
        val cues = SubtitleParser.parse(File(path), track.subFormat)
        cueCache[path] = cues
        return cues
    }

    /** 读文本类文件（台本 / 字幕原文） */
    fun readText(asset: Asset): String = try {
        SubtitleParser.decodeBytes(asset.file.readBytes()).take(400_000)
    } catch (e: Exception) {
        "读取失败：${e.message}"
    }

    // ---------------- 分类 ----------------

    private enum class Kind { AUDIO, IMAGE, SUB, DOC, OTHER, UNKNOWN }

    private val audioExts = setOf(
        "mp3", "mp4", "m4a", "m4b", "wav", "wave", "flac", "aac", "ogg", "oga", "opus",
        "wma", "aif", "aiff", "aifc", "amr", "3gp", "mka", "wv", "ape", "dsf", "dff",
        "mp2", "ac3", "caf", "w64", "rf64", "webm", "m4r", "m3u"
    )
    private val imageExts = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif")
    private val subExts = setOf("lrc", "srt", "vtt", "ass", "ssa", "txt", "sub", "sbv")
    private val docExts = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "epub",
        "html", "htm", "rtf", "md", "csv", "json", "xml"
    )

    private val folderCoverNames = setOf(
        "cover", "folder", "front", "albumart", "album", "jacket", "package",
        "封面", "表紙", "ジャケット", "パッケージ", "专辑封面"
    )

    private data class Picked(
        val uri: Uri,
        val name: String,
        val kind: Kind,
        val ext: String,
        val dir: String = "",
        val size: Long = 0
    )

    /**
     * 归一化文件名用于配对：
     * "Track01：xxx.wav.vtt" → "track01：xxx"（把尾部的音频扩展名也去掉）
     * 这样双扩展名的字幕（.wav.vtt / .mp3.lrc）也能和音频对上
     */
    private fun baseOf(name: String): String {
        var s = name.substringBeforeLast('.', name).lowercase().trim()
        while (true) {
            val dot = s.lastIndexOf('.')
            if (dot <= 0 || dot >= s.length - 1) break
            if (s.substring(dot + 1) in audioExts) s = s.substring(0, dot) else break
        }
        return s
    }

    private fun kindOf(name: String): Kind {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in audioExts -> Kind.AUDIO
            ext in imageExts -> Kind.IMAGE
            ext in subExts -> Kind.SUB
            ext in docExts -> Kind.DOC
            ext.isEmpty() -> Kind.UNKNOWN
            else -> Kind.OTHER
        }
    }

    /** 扩展名不认识时看文件头（有些 wav 被存成 .bin/.tmp，或者根本没扩展名） */
    private fun sniff(uri: Uri): Kind? {
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                val b = ByteArray(12)
                val n = ins.read(b)
                if (n < 4) return null
                val head = String(b, 0, 4, Charsets.ISO_8859_1)
                when {
                    head == "RIFF" || head == "fLaC" || head == "OggS" || head == "FORM" -> Kind.AUDIO
                    head.startsWith("ID3") -> Kind.AUDIO
                    head == "%PDF" -> Kind.DOC
                    b[0] == 0x89.toByte() -> Kind.IMAGE
                    b[0] == 0xFF.toByte() && n >= 2 && b[1] == 0xD8.toByte() -> Kind.IMAGE
                    head.startsWith("PK") -> Kind.OTHER
                    else -> Kind.OTHER
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun displayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (e: Exception) {
            null
        } ?: uri.lastPathSegment
    }

    private fun displaySize(uri: Uri): Long {
        if (uri.scheme == "file") {
            return try {
                uri.path?.let { File(it).length() } ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
        return try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) c.getLong(idx) else 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    // ---------------- 导入入口 ----------------

    suspend fun importUris(
        uris: List<Uri>,
        onProgress: ((Int, Int) -> Unit)? = null
    ): ImportResult = withContext(Dispatchers.IO) {
        val picked = ArrayList<Picked>()
        val skipped = ArrayList<String>()
        for (u in uris) {
            val name = displayName(u) ?: continue
            val raw = kindOf(name)
            val kind = if (raw == Kind.UNKNOWN) (sniff(u) ?: Kind.UNKNOWN) else raw
            if (kind == Kind.UNKNOWN) {
                skipped.add(name)
                continue
            }
            picked.add(
                Picked(u, name, kind, name.substringAfterLast('.', "").lowercase(), "", displaySize(u))
            )
        }
        buildImport(picked, kUnfiled, skipped, onProgress)
    }

    /** 选一个文件夹 = 一个专辑；子文件夹里的文件都归到这个专辑 */
    suspend fun importTree(
        treeUri: Uri,
        onProgress: ((Int, Int) -> Unit)? = null
    ): ImportResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(ctx, treeUri)
            ?: return@withContext ImportResult(0, 0, 0, emptyList(), emptyList(), 0, 0, emptyList())
        val album = root.name?.trim().orEmpty().ifBlank { "我的文件夹" }
        val picked = ArrayList<Picked>()
        val skipped = ArrayList<String>()

        fun walk(dir: DocumentFile, rel: String, depth: Int) {
            if (depth > 6) return
            val kids = try {
                dir.listFiles()
            } catch (e: Exception) {
                return
            }
            for (f in kids) {
                if (f.isDirectory) {
                    val n = f.name?.trim().orEmpty()
                    walk(f, if (rel.isEmpty()) n else "$rel/$n", depth + 1)
                } else {
                    val name = f.name ?: continue
                    if (f.length() <= 0L) {
                        skipped.add(name)
                        continue
                    }
                    val raw = kindOf(name)
                    val kind = if (raw == Kind.UNKNOWN) (sniff(f.uri) ?: Kind.UNKNOWN) else raw
                    if (kind == Kind.UNKNOWN) {
                        skipped.add(name)
                        continue
                    }
                    picked.add(
                        Picked(
                            f.uri, name, kind, name.substringAfterLast('.', "").lowercase(),
                            dir = rel, size = f.length()
                        )
                    )
                }
            }
        }
        walk(root, "", 0)
        buildImport(picked, album, skipped, onProgress)
    }

    // ---------------- 核心：配对 + 入库 ----------------

    private fun pickFolderCover(images: List<Picked>): Picked? {
        if (images.isEmpty()) return null
        images.firstOrNull { baseOf(it.name) in folderCoverNames }?.let { return it }
        return if (images.size == 1) images[0] else null
    }

    /** 专辑级配图：插图/画像/パッケージ 这类文件夹里的图，或者名字里带 イラスト/cover 的图 */
    private val artFolderNames = listOf(
        "插图", "插畫", "插画", "イラスト", "画像", "パッケージ", "package",
        "cover", "scan", "特典", "omake", "おまけ", "jacket", "photo", "写真"
    )
    private val artNameHints = listOf(
        "插图", "イラスト", "cover", "ジャケット", "パッケージ", "表紙", "jacket",
        "album", "パッケージイラスト", "メイン"
    )

    private fun pickAlbumCover(images: List<Picked>): Picked? {
        if (images.isEmpty()) return null
        val inArtFolder = images.filter { p ->
            val folder = p.dir.substringAfterLast('/').lowercase()
            folder.isNotEmpty() && artFolderNames.any { folder.contains(it) }
        }
        val byName = images.filter { p ->
            val b = baseOf(p.name)
            artNameHints.any { b.contains(it) }
        }
        val pool = (inArtFolder + byName).distinct()
        if (pool.isNotEmpty()) return pool.maxByOrNull { it.size }
        return if (images.size == 1) images[0] else null
    }

    private fun buildImport(
        picked: List<Picked>,
        album: String,
        skipped: MutableList<String>,
        onProgress: ((Int, Int) -> Unit)? = null
    ): ImportResult {
        val existing = load()
        val assets = loadAssets()

        // 按「目录 + 文件名」配对，避免不同子目录同名文件互相错配
        val groups = LinkedHashMap<String, MutableList<Picked>>()
        for (p in picked) {
            if (p.kind == Kind.AUDIO || p.kind == Kind.IMAGE || p.kind == Kind.SUB) {
                groups.getOrPut(p.dir + "|" + baseOf(p.name)) { mutableListOf() }.add(p)
            }
        }
        val dirImages = picked.filter { it.kind == Kind.IMAGE }.groupBy { it.dir }
        val albumImages = picked.filter { it.kind == Kind.IMAGE }

        var addedCount = 0
        var subMatched = 0
        var coverMatched = 0
        var missingCover = 0
        val failed = ArrayList<String>()
        val usedUris = HashSet<Uri>()

        val audioGroups = groups.filterValues { g -> g.any { it.kind == Kind.AUDIO } }

        val totalAudio = audioGroups.size
        var doneAudio = 0
        for ((_, g) in audioGroups) {
            val audio = g.first { it.kind == Kind.AUDIO }
            val subList = g.filter { it.kind == Kind.SUB }
            val sub = subList.sortedBy { subPriority(it.ext) }.firstOrNull()
            val img = g.firstOrNull { it.kind == Kind.IMAGE }
                ?: pickFolderCover(dirImages[audio.dir].orEmpty())
                ?: pickAlbumCover(albumImages)

            val title = audio.name.substringBeforeLast('.', audio.name).trim()
            val fresh = doImport(audio, title, img, sub, album, existing, failed)
            if (fresh != null) {
                existing.add(fresh)
                addedCount++
                if (fresh.coverPath != null) coverMatched++ else missingCover++
                if (fresh.hasSub) subMatched++
                if (img != null) usedUris.add(img.uri)
                subList.forEach { usedUris.add(it.uri) }
            }
            doneAudio++
            onProgress?.invoke(doneAudio, totalAudio)
        }

        // 漏网字幕：挂到同专辑里还没字幕、名字能对上的音轨
        val leftovers = picked.filter { it.kind == Kind.SUB && it.uri !in usedUris }
        for (o in leftovers) {
            val target = existing.lastOrNull { t ->
                t.folder == album && !t.hasSub && t.title.isNotEmpty() &&
                    (baseOf(o.name) == t.title.lowercase() ||
                        baseOf(o.name).startsWith(t.title.lowercase()) ||
                        t.title.lowercase().startsWith(baseOf(o.name)))
            } ?: continue
            val newSub = copyAndValidateSub(o, target.id) ?: continue
            val idx = existing.indexOf(target)
            existing[idx] = target.copy(subPath = newSub.first, subFormat = newSub.second)
            subMatched++
            usedUris.add(o.uri)
        }

        // 非音频文件全部作为专辑附件保留（图片 / 字幕 / 文档 / 其他）
        var assetCount = 0
        val seen = HashSet<String>()
        for (p in picked.filter { it.kind != Kind.AUDIO }) {
            val key = p.name.lowercase() + "|" + p.size
            if (!seen.add(key)) continue
            val asset = copyAsset(p, album)
            if (asset != null) {
                assets.add(asset)
                assetCount++
            } else {
                failed.add(p.name)
            }
        }

        save(existing)
        saveAssets(assets)
        return ImportResult(
            added = addedCount,
            subtitleMatched = subMatched,
            coverMatched = coverMatched,
            failed = failed,
            missingCover = missingCover,
            assetsAdded = assetCount,
            skipped = skipped
        )
    }

    private fun subPriority(ext: String): Int = when (ext) {
        "ass", "ssa" -> 0
        "srt", "vtt" -> 1
        "lrc" -> 2
        else -> 3
    }

    private fun doImport(
        audio: Picked,
        title: String,
        img: Picked?,
        sub: Picked?,
        album: String,
        existing: List<Track>,
        failed: MutableList<String>
    ): Track? {
        return try {
            val id = UUID.randomUUID().toString()
            val destAudio = File(libDir, "$id.${audio.ext.ifEmpty { "bin" }}")
            copyTo(audio.uri, destAudio)
            if (destAudio.length() <= 0L) {
                destAudio.delete()
                failed.add(audio.name)
                return null
            }
            val meta = readMeta(destAudio)

            // 去重：同名 + 同大小才算重复（多碟同名曲目不会再被吃掉）
            val dup = existing.any { t ->
                t.title.equals(title, true) &&
                    audio.size > 0 && t.sizeBytes > 0 && t.sizeBytes == audio.size
            }
            if (dup) {
                destAudio.delete()
                return null
            }

            var coverPath: String? = null
            if (img != null) {
                val ext = img.ext.ifEmpty { "jpg" }
                val dest = File(coverDir, "$id.$ext")
                try {
                    copyTo(img.uri, dest)
                    if (dest.length() > 0) coverPath = dest.absolutePath else dest.delete()
                } catch (e: Exception) {
                    dest.delete()
                }
            }
            if (coverPath == null && meta.artwork != null) {
                val ext = if (meta.artworkMime.contains("png", true)) "png" else "jpg"
                val dest = File(coverDir, "$id.$ext")
                dest.writeBytes(meta.artwork)
                coverPath = dest.absolutePath
            }

            var subPath: String? = null
            var subFormat = SubFormat.NONE
            if (sub != null) {
                val r = copyAndValidateSub(sub, id)
                if (r != null) {
                    subPath = r.first
                    subFormat = r.second
                }
            }

            val finalTitle = cleanTitle(meta.title?.takeIf { it.isNotBlank() } ?: title)
            Track(
                id = id,
                title = finalTitle,
                audioPath = destAudio.absolutePath,
                coverPath = coverPath,
                subPath = subPath,
                subFormat = subFormat,
                durationMs = meta.duration,
                lastPosMs = 0,
                addedAt = System.currentTimeMillis(),
                folder = album,
                sizeBytes = destAudio.length(),
                relDir = audio.dir,
                srcName = audio.name
            )
        } catch (e: Exception) {
            failed.add(audio.name + "（" + (e.message ?: "读取失败") + "）")
            null
        }
    }

    private fun copyAndValidateSub(sub: Picked, id: String): Pair<String, SubFormat>? {
        return try {
            val dest = File(subDir, "$id.${sub.ext}")
            copyTo(sub.uri, dest)
            val fmt = SubFormat.fromExt(sub.ext)
            val cues = SubtitleParser.parse(dest, fmt)
            if (cues.isEmpty() && fmt == SubFormat.NONE) {
                dest.delete()
                return null
            }
            Pair(dest.absolutePath, fmt)
        } catch (e: Exception) {
            null
        }
    }

    private fun copyAsset(p: Picked, album: String): Asset? {
        return try {
            if (p.size > 400L * 1024 * 1024) return null
            val id = UUID.randomUUID().toString()
            val ext = p.ext.ifEmpty { "bin" }
            val dest = File(assetDir, "$id.$ext")
            copyTo(p.uri, dest)
            if (dest.length() <= 0L) {
                dest.delete()
                return null
            }
            Asset(
                id = id,
                album = album,
                name = p.name,
                path = dest.absolutePath,
                kind = when (p.kind) {
                    Kind.IMAGE -> AssetKind.IMAGE.name
                    Kind.SUB -> AssetKind.SUBTITLE.name
                    Kind.DOC -> AssetKind.DOC.name
                    else -> AssetKind.OTHER.name
                },
                sizeBytes = dest.length(),
                relDir = p.dir
            )
        } catch (e: Exception) {
            null
        }
    }

    // ---------------- 后置修改 ----------------

    suspend fun attachSubtitle(track: Track, uri: Uri): Track? = withContext(Dispatchers.IO) {
        val name = displayName(uri) ?: return@withContext null
        val ext = name.substringAfterLast('.', "").lowercase()
        val picked = Picked(uri, name, Kind.SUB, ext, "", displaySize(uri))
        val r = copyAndValidateSub(picked, track.id) ?: return@withContext null
        cueCache.remove(track.subPath)
        track.copy(subPath = r.first, subFormat = r.second)
    }

    suspend fun attachCover(track: Track, uri: Uri): Track? = withContext(Dispatchers.IO) {
        val path = copyCoverFile(track.id, uri) ?: return@withContext null
        track.copy(coverPath = path)
    }

    /** 把一张封面应用到整个专辑 */
    suspend fun attachCoverToAlbum(
        tracks: List<Track>,
        album: String,
        uri: Uri
    ): List<Track> = withContext(Dispatchers.IO) {
        val path = copyCoverFile("album_" + UUID.randomUUID(), uri) ?: return@withContext tracks
        tracks.map { if (it.folder == album) it.copy(coverPath = path) else it }
    }

    private fun copyCoverFile(idSeed: String, uri: Uri): String? {
        return try {
            val name = displayName(uri) ?: "cover.jpg"
            val ext = name.substringAfterLast('.', "").lowercase().ifEmpty { "jpg" }
            val dest = File(coverDir, "${idSeed}_${System.currentTimeMillis()}.$ext")
            // 只读取源文件，绝不修改或删除它；这里记录一下便于排查"源文件不见了"的反馈
            android.util.Log.i("koebox", "选封面：uri=$uri name=$name 预计大小=${displaySize(uri)}")
            copyTo(uri, dest)
            val stillThere = try {
                ctx.contentResolver.openInputStream(uri)?.use { true } ?: false
            } catch (e: Exception) {
                false
            }
            android.util.Log.i("koebox", "复制完成：目标=${dest.length()}B，源文件仍可读=$stillThere")
            if (dest.length() <= 0L) {
                dest.delete()
                null
            } else {
                dest.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    fun delete(track: Track) {
        runCatching { File(track.audioPath).delete() }
        track.subPath?.let { runCatching { File(it).delete() } }
        track.coverPath?.let { runCatching { File(it).delete() } }
        cueCache.remove(track.subPath)
    }

    fun deleteAssets(list: List<Asset>) {
        list.forEach { runCatching { File(it.path).delete() } }
    }

    private fun copyTo(src: Uri, dest: File) {
        ctx.contentResolver.openInputStream(src)?.use { input ->
            dest.outputStream().use { out -> input.copyTo(out, DEFAULT_BUFFER) }
        } ?: throw IOException("无法打开文件")
    }

    private data class Meta(
        val duration: Long,
        val title: String?,
        val artwork: ByteArray?,
        val artworkMime: String = "image/jpeg"
    )

    private fun readMeta(file: File): Meta {
        var duration = 0L
        var title: String? = null
        var art: ByteArray? = null
        var mime = "image/jpeg"
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            art = mmr.embeddedPicture
        } catch (_: Exception) {
        } finally {
            runCatching { mmr.release() }
        }
        // 有些 WAV 带 JUNK/bext 块，系统读不出时长，自己按块结构算
        if (duration <= 0L) duration = riffDurationMs(file)
        if (art == null) {
            val id3 = Id3.read(file)
            if (id3 != null) {
                art = id3.picture
                mime = id3.pictureMime ?: "image/jpeg"
                if (title.isNullOrBlank()) title = id3.title
            }
        }
        if (art != null && art.size > 8 && art[0] == 0x89.toByte() && art[1] == 'P'.code.toByte()) {
            mime = "image/png"
        }
        return Meta(duration, title, art, mime)
    }

    /** 按 RIFF 块结构算时长：data 字节数 / byteRate，能跳过 JUNK、bext 等块 */
    private fun riffDurationMs(file: File): Long {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val head = ByteArray(12)
                raf.readFully(head)
                val riff = String(head, 0, 4, Charsets.ISO_8859_1)
                val wave = String(head, 8, 4, Charsets.ISO_8859_1)
                if (riff != "RIFF" || wave != "WAVE") return 0L
                val total = raf.length()
                var pos = 12L
                var byteRate = 0L
                var guard = 0
                while (pos + 8 <= total && guard < 64) {
                    guard++
                    raf.seek(pos)
                    val hdr = ByteArray(8)
                    raf.readFully(hdr)
                    val id = String(hdr, 0, 4, Charsets.ISO_8859_1)
                    val sz = leInt(hdr, 4)
                    val size = if (sz < 0) total - pos - 8 else sz.toLong()
                    when (id) {
                        "fmt " -> {
                            raf.seek(pos + 8)
                            val f = ByteArray(16)
                            raf.readFully(f)
                            byteRate = (leInt(f, 8).toLong() and 0xFFFFFFFFL)
                        }
                        "data" -> {
                            val realSize = if (size <= 0 || pos + 8 + size > total) total - pos - 8 else size
                            return if (byteRate > 0) realSize * 1000L / byteRate else 0L
                        }
                    }
                    pos += 8 + size + (size and 1L)
                }
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun leInt(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    companion object {
        private const val DEFAULT_BUFFER = 64 * 1024
        const val kUnfiled = "未分类"
    }
}
