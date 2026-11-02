package com.koebox.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.koebox.app.data.Asset
import com.koebox.app.data.AssetKind
import com.koebox.app.data.Fmt
import com.koebox.app.data.SubtitleParser
import com.koebox.app.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 相对路径 rel 在 prefix 之下的下一层目录名，不在其下返回 null */
private fun nextSegment(rel: String, prefix: String): String? {
    if (prefix.isEmpty()) {
        if (rel.isEmpty()) return null
        return rel.substringBefore('/')
    }
    if (rel == prefix) return null
    if (!rel.startsWith("$prefix/")) return null
    return rel.removePrefix("$prefix/").substringBefore('/')
}

private fun insideFolder(rel: String, prefix: String, name: String): Boolean {
    val target = if (prefix.isEmpty()) name else "$prefix/$name"
    return rel == target || rel.startsWith("$target/")
}

@Composable
fun AlbumScreen(
    vm: MainViewModel,
    folder: String,
    playingId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenPlayer: () -> Unit,
    onAttachSub: (Track) -> Unit,
    onAttachCover: (Track) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    var viewerAsset by remember { mutableStateOf<Asset?>(null) }
    var path by remember(folder) { mutableStateOf<List<String>>(emptyList()) }

    val viewing = viewerAsset
    if (viewing != null) {
        AssetViewer(asset = viewing, onClose = { viewerAsset = null })
        return
    }

    BackHandler {
        if (path.isNotEmpty()) path = path.dropLast(1) else onBack()
    }

    val group = vm.folderGroups().firstOrNull { it.name == folder }
    val allTracks = group?.tracks ?: emptyList()
    val allAssets = vm.assetsOfAlbum(folder)
    val prefix = path.joinToString("/")

    val directTracks = allTracks.filter { it.relDir == prefix }
    val directAssets = allAssets.filter { it.relDir == prefix }
    val subNames = (
        allTracks.mapNotNull { nextSegment(it.relDir, prefix) } +
            allAssets.mapNotNull { nextSegment(it.relDir, prefix) }
        ).distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun countIn(name: String): Int =
        allTracks.count { insideFolder(it.relDir, prefix, name) } +
            allAssets.count { insideFolder(it.relDir, prefix, name) }

    val playable = if (directTracks.isNotEmpty()) directTracks else allTracks
    val cover = rememberCover(group?.coverPath, 640)
    val currentTrack = vm.tracks.firstOrNull { it.id == playingId }

    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var menuAsset by remember { mutableStateOf<Asset?>(null) }
    var deleteTarget by remember { mutableStateOf<Track?>(null) }
    var deleteAssetTarget by remember { mutableStateOf<Asset?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 14.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (path.isNotEmpty()) path = path.dropLast(1) else onBack() }) {
                    Icon(
                        Icons.Rounded.ArrowBackIosNew, null,
                        tint = TextMain,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (playable.isNotEmpty()) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Lav.copy(alpha = 0.16f))
                            .clickable { onPlay(playable, 0) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow, null,
                            tint = Lav,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text("播放全部", color = Lav, fontSize = 12.sp)
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface1)
                ) {
                    if (cover != null) {
                        Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        CoverPlaceholder(Modifier.fillMaxSize())
                        Icon(
                            Icons.Rounded.MusicNote, null,
                            tint = Lav.copy(alpha = 0.4f),
                            modifier = Modifier.align(Alignment.Center).size(30.dp)
                        )
                    }
                }
            }

            Text(
                folder,
                color = TextMain,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)
            )
            Text(
                buildString {
                    append("${allTracks.size} 首")
                    val total = group?.totalMs ?: 0L
                    if (total > 0) append(" · ${Fmt.short(total)}")
                    if (allAssets.isNotEmpty()) append(" · ${allAssets.size} 个附件")
                },
                color = TextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Crumb(text = folder, active = path.isEmpty()) { path = emptyList() }
                path.forEachIndexed { i, seg ->
                    Icon(
                        Icons.Rounded.KeyboardArrowRight, null,
                        tint = TextDim.copy(alpha = 0.6f),
                        modifier = Modifier.size(15.dp)
                    )
                    Crumb(text = seg, active = i == path.lastIndex) { path = path.take(i + 1) }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(1.dp)
                    .background(Surface2)
            )

            LazyColumn(
                contentPadding = PaddingValues(
                    start = 10.dp, end = 10.dp, top = 6.dp,
                    bottom = if (currentTrack != null) 130.dp else 24.dp
                )
            ) {
                items(subNames, key = { "dir:$prefix/$it" }) { name ->
                    FolderRow(name = name, count = countIn(name)) { path = path + name }
                }

                itemsIndexed(directTracks, key = { _, t -> t.id }) { index, t ->
                    TrackFileRow(
                        track = t,
                        index = index,
                        isCurrent = t.id == playingId,
                        isPlaying = isPlaying && t.id == playingId,
                        onPlay = { onPlay(directTracks, index) },
                        onMenu = { menuTrack = t }
                    )
                }

                items(directAssets, key = { it.id }) { a ->
                    AssetFileRow(
                        asset = a,
                        onClick = {
                            if (a.isTextReadable || a.kindEnum == AssetKind.IMAGE ||
                                a.kindEnum == AssetKind.SUBTITLE
                            ) {
                                viewerAsset = a
                            } else {
                                openExternally(context, a)
                            }
                        },
                        onMenu = { menuAsset = a }
                    )
                }

                if (subNames.isEmpty() && directTracks.isEmpty() && directAssets.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("这个文件夹是空的", color = TextDim, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (currentTrack != null) {
            MiniPlayerBar(
                track = currentTrack,
                isPlaying = isPlaying,
                onOpen = onOpenPlayer,
                onToggle = onToggle,
                onNext = onNext,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }

    menuTrack?.let { mt ->
        TrackActionDialog(
            track = mt,
            onDismiss = { menuTrack = null },
            onAttachSub = { menuTrack = null; onAttachSub(mt) },
            onAttachCover = { menuTrack = null; onAttachCover(mt) },
            onDelete = { menuTrack = null; deleteTarget = mt }
        )
    }

    menuAsset?.let { a ->
        AlertDialog(
            onDismissRequest = { menuAsset = null },
            containerColor = Surface1,
            titleContentColor = TextMain,
            textContentColor = TextDim,
            title = { Text(a.name, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    DialogRow("打开 / 查看") {
                        menuAsset = null
                        if (a.isTextReadable || a.kindEnum == AssetKind.IMAGE ||
                            a.kindEnum == AssetKind.SUBTITLE
                        ) {
                            viewerAsset = a
                        } else {
                            openExternally(context, a)
                        }
                    }
                    DialogRow("用其他应用打开") {
                        menuAsset = null
                        openExternally(context, a)
                    }
                    DialogRow("从专辑里删除") {
                        menuAsset = null
                        deleteAssetTarget = a
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuAsset = null }) { Text("关闭", color = Lav) }
            }
        )
    }

    deleteTarget?.let { dt ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = Surface1,
            titleContentColor = TextMain,
            textContentColor = TextDim,
            title = { Text("删除？", fontSize = 16.sp) },
            text = { Text("会同时删掉本地保存的音频文件、字幕和封面。", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(dt)
                    deleteTarget = null
                }) { Text("删除", color = Pink) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消", color = TextDim) }
            }
        )
    }

    deleteAssetTarget?.let { da ->
        AlertDialog(
            onDismissRequest = { deleteAssetTarget = null },
            containerColor = Surface1,
            titleContentColor = TextMain,
            textContentColor = TextDim,
            title = { Text("删除附件？", fontSize = 16.sp) },
            text = { Text(da.name, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAsset(da)
                    deleteAssetTarget = null
                }) { Text("删除", color = Pink) }
            },
            dismissButton = {
                TextButton(onClick = { deleteAssetTarget = null }) { Text("取消", color = TextDim) }
            }
        )
    }
}

@Composable
private fun Crumb(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (active) TextMain else Lav.copy(alpha = 0.85f),
        fontSize = 11.sp,
        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = !active) { onClick() }
            .padding(horizontal = 4.dp, vertical = 3.dp)
    )
}

@Composable
private fun FolderRow(name: String, count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Folder, null,
            tint = Lav.copy(alpha = 0.75f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = TextMain,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("$count 个文件", color = TextDim, fontSize = 10.sp)
        }
        Icon(
            Icons.Rounded.KeyboardArrowRight, null,
            tint = TextDim.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TrackFileRow(
    track: Track,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPlay() }
            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPlay, modifier = Modifier.size(34.dp)) {
            Icon(
                if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                null,
                tint = if (isCurrent) Pink else Lav,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "$index. ${track.title}",
                color = if (isCurrent) TextMain else TextMain.copy(alpha = 0.94f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    File(track.audioPath).extension.uppercase() + " · " + Fmt.short(track.durationMs),
                    color = TextDim,
                    fontSize = 10.sp
                )
                if (track.hasSub) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        Icons.Rounded.Subtitles, null,
                        tint = Lav.copy(alpha = 0.8f),
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(track.subFormat.label, color = Lav.copy(alpha = 0.8f), fontSize = 9.sp)
                }
            }
        }
        IconButton(onClick = onMenu, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Rounded.MoreHoriz, null,
                tint = TextDim,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun AssetFileRow(asset: Asset, onClick: () -> Unit, onMenu: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                when (asset.kindEnum) {
                    AssetKind.IMAGE -> Icons.Rounded.BrokenImage
                    AssetKind.SUBTITLE -> Icons.Rounded.Subtitles
                    else -> Icons.Rounded.Description
                },
                null,
                tint = Lav.copy(alpha = 0.8f),
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                asset.name,
                color = TextMain.copy(alpha = 0.94f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                kindLabel(asset) + " · " + humanSize(asset.sizeBytes),
                color = TextDim,
                fontSize = 10.sp
            )
        }
        IconButton(onClick = onMenu, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Rounded.MoreHoriz, null,
                tint = TextDim,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

private fun kindLabel(a: Asset): String = when (a.kindEnum) {
    AssetKind.IMAGE -> "图片"
    AssetKind.SUBTITLE -> "字幕"
    AssetKind.DOC -> {
        val ext = a.name.substringAfterLast('.', "").lowercase()
        when (ext) {
            "pdf" -> "PDF 文档"
            "txt" -> "文本文件"
            "doc", "docx" -> "Word 文档"
            else -> "文档"
        }
    }
    AssetKind.OTHER -> "文件"
}

private fun humanSize(bytes: Long): String = when {
    bytes <= 0 -> "-"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024f)
    else -> String.format("%.1f MB", bytes / 1024f / 1024f)
}

/** 全屏看图片 / 读文本 */
@Composable
private fun AssetViewer(asset: Asset, onClose: () -> Unit) {
    BackHandler { onClose() }

    if (asset.kindEnum == AssetKind.IMAGE) {
        val bmp = rememberCover(asset.path, 2000)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() }
        ) {
            if (bmp != null) {
                Image(
                    bmp, null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            } else {
                Text(
                    "图片打不开",
                    color = TextDim,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    asset.name,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, null, tint = Color.White)
                }
            }
        }
        return
    }

    val text by produceState(initialValue = "读取中…", asset.id) {
        value = withContext(Dispatchers.IO) {
            try {
                SubtitleParser.decodeBytes(asset.file.readBytes()).take(600_000)
            } catch (e: Exception) {
                "读取失败：${e.message}"
            }
        }
    }
    val scroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                asset.name,
                color = TextMain,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, null, tint = TextDim)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(
                text,
                color = TextMain.copy(alpha = 0.9f),
                fontSize = 13.sp,
                lineHeight = 21.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun openExternally(context: Context, asset: Asset) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            asset.file
        )
        val ext = asset.name.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(intent, "用其他应用打开"))
    } catch (e: Exception) {
        // 没有能打开的应用就不动作
    }
}
