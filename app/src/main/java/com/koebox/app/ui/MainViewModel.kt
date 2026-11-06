package com.koebox.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.koebox.app.data.Asset
import com.koebox.app.data.FolderGroup
import com.koebox.app.data.ImportResult
import com.koebox.app.data.LibraryRepo
import com.koebox.app.data.Prefs
import com.koebox.app.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val repo = LibraryRepo(app)
    val prefs = Prefs(app)

    val tracks = mutableStateListOf<Track>()
    val assets = mutableStateListOf<Asset>()

    var busy by mutableStateOf(false)
        private set

    var busyText by mutableStateOf("正在导入…")

    /** 悬浮字幕开关（同时写进 Prefs，App 里读这个状态来显示悬浮条） */
    var floatingSub by mutableStateOf(prefs.floatingSub)

    fun setFloating(v: Boolean) {
        floatingSub = v
        prefs.floatingSub = v
    }

    var message by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch { reload() }
    }

    suspend fun reload() {
        val list = withContext(Dispatchers.IO) { repo.load() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        val assetList = withContext(Dispatchers.IO) { repo.loadAssets() }
        tracks.clear()
        tracks.addAll(list)
        assets.clear()
        assets.addAll(assetList)
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busy = true
            busyText = "正在导入…"
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    repo.importUris(uris) { done, total ->
                        busyText = if (total > 0) "正在导入 $done/$total…" else "正在导入…"
                    }
                }
            }.getOrNull()
            reload()
            busy = false
            message = if (r == null) "导入出错了，换几个文件再试" else describe(r)
        }
    }

    fun importTree(uri: Uri) {
        viewModelScope.launch {
            busy = true
            busyText = "正在扫描文件夹…"
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    repo.importTree(uri) { done, total ->
                        busyText = if (total > 0) "正在导入 $done/$total…" else "正在导入…"
                    }
                }
            }.getOrNull()
            reload()
            busy = false
            message = when {
                r == null -> "导入出错了，换个文件夹再试"
                r.added == 0 && r.assetsAdded == 0 -> "这个文件夹里没找到能识别的文件"
                else -> describe(r)
            }
        }
    }

    private fun describe(r: ImportResult): String {
        val sb = StringBuilder()
        sb.append("新增 ${r.added} 个音轨")
        if (r.subtitleMatched > 0) sb.append("，字幕 ${r.subtitleMatched}")
        if (r.coverMatched > 0) sb.append("，封面 ${r.coverMatched}")
        if (r.missingCover > 0) sb.append("（${r.missingCover} 个没找到封面）")
        if (r.assetsAdded > 0) sb.append("，附件 ${r.assetsAdded}")
        if (r.failed.isNotEmpty()) sb.append("；${r.failed.size} 个文件读取失败")
        if (r.skipped.isNotEmpty()) sb.append("；${r.skipped.size} 个文件识别不了")
        return sb.toString()
    }

    /** 按文件夹（专辑）分组 */
    fun folderGroups(): List<FolderGroup> {
        val map = LinkedHashMap<String, MutableList<Track>>()
        for (t in tracks) {
            val name = t.folder.ifBlank { LibraryRepo.kUnfiled }
            map.getOrPut(name) { mutableListOf() }.add(t)
        }
        return map.map { (name, list) ->
            FolderGroup(
                name,
                list.sortedWith(
                    compareBy<Track> { it.relDir }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.srcName.ifBlank { it.title } }
                )
            )
        }.sortedWith(
            compareBy<FolderGroup> { it.name == LibraryRepo.kUnfiled }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
    }

    fun tracksOfFolder(folder: String): List<Track> =
        folderGroups().firstOrNull { it.name == folder }?.tracks ?: emptyList()

    fun assetsOfAlbum(album: String): List<Asset> =
        assets.filter { it.album == album }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    fun renameFolder(oldName: String, newName: String) {
        val target = newName.trim()
        if (target.isEmpty() || target == oldName) return
        for (i in tracks.indices) {
            if (tracks[i].folder.ifBlank { LibraryRepo.kUnfiled } == oldName) {
                tracks[i] = tracks[i].copy(folder = target)
            }
        }
        for (i in assets.indices) {
            if (assets[i].album == oldName) {
                assets[i] = assets[i].copy(album = target)
            }
        }
        saveAll()
        viewModelScope.launch(Dispatchers.IO) { repo.saveAssets(assets.toList()) }
    }

    fun deleteFolder(folder: String) {
        val list = tracks.filter { it.folder.ifBlank { LibraryRepo.kUnfiled } == folder }
        val assetList = assets.filter { it.album == folder }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                list.forEach { repo.delete(it) }
                repo.deleteAssets(assetList)
            }
            reload()
            message = "已删除专辑「$folder」（${list.size} 个音轨，${assetList.size} 个附件）"
        }
    }

    fun delete(track: Track) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(track) }
            reload()
            message = "已删除「${track.title}」"
        }
    }

    fun deleteAsset(asset: Asset) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.deleteAssets(listOf(asset))
            }
            reload()
        }
    }

    fun replaceTrack(track: Track) {
        val i = tracks.indexOfFirst { it.id == track.id }
        if (i >= 0) {
            tracks[i] = track
        }
        viewModelScope.launch(Dispatchers.IO) { repo.save(tracks.toList()) }
    }

    fun attachSubtitle(track: Track, uri: Uri) {
        viewModelScope.launch {
            busy = true
            val updated = withContext(Dispatchers.IO) { repo.attachSubtitle(track, uri) }
            busy = false
            if (updated != null) {
                replaceTrack(updated)
                message = "字幕已挂载"
            } else {
                message = "这个文件里没有解析到时间轴字幕"
            }
        }
    }

    fun attachCover(track: Track, uri: Uri) {
        viewModelScope.launch {
            busy = true
            val updated = withContext(Dispatchers.IO) { repo.attachCover(track, uri) }
            busy = false
            if (updated != null) {
                replaceTrack(updated)
                message = "封面已更换（只改了「${track.title}」）"
            } else {
                message = "封面读取失败"
            }
        }
    }

    /** 把封面应用到整个专辑 */
    fun attachCoverToAlbum(album: String, uri: Uri) {
        viewModelScope.launch {
            busy = true
            val list = tracks.toList()
            val updated = withContext(Dispatchers.IO) {
                repo.attachCoverToAlbum(list, album, uri)
            }
            busy = false
            val changed = updated.count { t -> t.folder == album }
            tracks.clear()
            tracks.addAll(updated.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }))
            viewModelScope.launch(Dispatchers.IO) { repo.save(tracks.toList()) }
            message = if (changed > 0) "「$album」里的 $changed 首封面都换成新的了" else "封面读取失败"
        }
    }

    fun savePosition(id: String, pos: Long) {
        val i = tracks.indexOfFirst { it.id == id }
        if (i < 0) return
        val t = tracks[i]
        if (kotlin.math.abs(t.lastPosMs - pos) < 1000) return
        tracks[i] = t.copy(lastPosMs = pos)
        prefs.lastTrackId = id
        viewModelScope.launch(Dispatchers.IO) { repo.save(tracks.toList()) }
    }

    fun saveAll() {
        viewModelScope.launch(Dispatchers.IO) { repo.save(tracks.toList()) }
    }
}
