package com.koebox.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koebox.app.data.Fmt
import com.koebox.app.data.FolderGroup
import com.koebox.app.data.Track

@Composable
fun LibraryScreen(
    vm: MainViewModel,
    playingId: String?,
    isPlaying: Boolean,
    onOpenAlbum: (String) -> Unit,
    onPlayList: (List<Track>, Int) -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onOpenPlayer: () -> Unit,
    onAttachSub: (Track) -> Unit,
    onAttachCover: (Track) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpenTimer: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Track?>(null) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var folderMenu by remember { mutableStateOf<FolderGroup?>(null) }
    var renameTarget by remember { mutableStateOf<FolderGroup?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteFolderTarget by remember { mutableStateOf<FolderGroup?>(null) }

    val all = vm.tracks.toList()
    val folders = vm.folderGroups()
    // -1 表示还没手动选过，按数据自动决定默认视图
    var modeSel by remember { mutableStateOf(-1) }
    val mode = if (modeSel >= 0) modeSel else if (folders.isNotEmpty()) 0 else 1

    val filteredTracks = if (query.isBlank()) all else all.filter { it.title.contains(query, true) }
    val filteredFolders = if (query.isBlank()) folders else folders.filter { it.name.contains(query, true) }
    val subCount = all.count { it.hasSub }
    val currentTrack = all.firstOrNull { it.id == playingId }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "声匣",
                        color = TextMain,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        if (all.isEmpty()) "把音声装进来吧"
                        else "${folders.size} 个文件夹 · ${all.size} 个音轨 · $subCount 带字幕",
                        color = TextDim,
                        fontSize = 11.sp
                    )
                }
                TimerPill(vm = vm, onClick = onOpenTimer, modifier = Modifier.padding(end = 6.dp))
                if (all.isNotEmpty()) {
                    SegmentedSwitch(
                        options = listOf("文件夹", "全部"),
                        selected = mode,
                        onSelect = { modeSel = it }
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface1)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = TextDim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("搜索标题或文件夹…", color = TextDim.copy(alpha = 0.7f), fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextMain, fontSize = 14.sp),
                        cursorBrush = SolidColor(Lav),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close, null, tint = TextDim,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { query = "" }
                    )
                }
            }

            if (all.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PillButton("选择文件", Icons.Rounded.FolderOpen, Modifier.weight(1f), onPickFiles)
                    PillButton("选择文件夹", Icons.Rounded.CreateNewFolder, Modifier.weight(1f), onPickFolder)
                }
            }

            val bottomPad = if (currentTrack != null) 130.dp else 24.dp

            if (all.isEmpty()) {
                EmptyState(onPickFiles, onPickFolder)
            } else if (mode == 0) {
                if (filteredFolders.isEmpty()) {
                    EmptySearch()
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = bottomPad),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredFolders, key = { it.name }) { f ->
                            FolderCard(
                                group = f,
                                onClick = { onOpenAlbum(f.name) },
                                onLongClick = { folderMenu = f }
                            )
                        }
                    }
                }
            } else {
                if (filteredTracks.isEmpty()) {
                    EmptySearch()
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = bottomPad),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredTracks, key = { it.id }) { track ->
                            val index = filteredTracks.indexOfFirst { it.id == track.id }
                            TrackCard(
                                track = track,
                                isCurrent = track.id == playingId,
                                isPlaying = isPlaying && track.id == playingId,
                                onClick = { onPlayList(filteredTracks, index) },
                                onLongClick = { menuTrack = track }
                            )
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

        if (vm.busy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Lav, strokeWidth = 3.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(vm.busyText, color = TextMain, fontSize = 14.sp)
                }
            }
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

    folderMenu?.let { f ->
        AlertDialog(
            onDismissRequest = { folderMenu = null },
            containerColor = Surface1,
            titleContentColor = TextMain,
            textContentColor = TextDim,
            title = { Text(f.name, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    DialogRow("重命名文件夹") {
                        renameText = f.name
                        renameTarget = f
                        folderMenu = null
                    }
                    DialogRow("删除整个文件夹（${f.count} 个音轨）") {
                        deleteFolderTarget = f
                        folderMenu = null
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { folderMenu = null }) { Text("关闭", color = Lav) }
            }
        )
    }

    renameTarget?.let { f ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = Surface1,
            titleContentColor = TextMain,
            title = { Text("重命名文件夹", fontSize = 16.sp) },
            text = {
                BasicTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextMain, fontSize = 15.sp),
                    cursorBrush = SolidColor(Lav),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface2)
                        .padding(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameFolder(f.name, renameText)
                    renameTarget = null
                }) { Text("保存", color = Lav) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消", color = TextDim) }
            }
        )
    }

    deleteFolderTarget?.let { f ->
        AlertDialog(
            onDismissRequest = { deleteFolderTarget = null },
            containerColor = Surface1,
            titleContentColor = TextMain,
            textContentColor = TextDim,
            title = { Text("删除「${f.name}」？", fontSize = 16.sp) },
            text = { Text("会删掉这个文件夹里 ${f.count} 个音轨的本地副本，原文件不受影响。", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFolder(f.name)
                    deleteFolderTarget = null
                }) { Text("删除", color = Pink) }
            },
            dismissButton = {
                TextButton(onClick = { deleteFolderTarget = null }) { Text("取消", color = TextDim) }
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(group: FolderGroup, onClick: () -> Unit, onLongClick: () -> Unit) {
    val cover = rememberCover(group.coverPath, 700)
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.94f)
                .clip(RoundedCornerShape(16.dp))
                .background(Surface1)
        ) {
            if (cover != null) {
                Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                CoverPlaceholder(Modifier.fillMaxSize())
                Icon(
                    Icons.Rounded.MusicNote, null,
                    tint = Lav.copy(alpha = 0.42f),
                    modifier = Modifier.align(Alignment.Center).size(38.dp)
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            group.name,
            color = TextMain,
            fontSize = 13.5.sp,
            lineHeight = 18.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${group.count} 首 · ${Fmt.short(group.totalMs)}",
            color = TextDim,
            fontSize = 11.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackCard(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val cover = rememberCover(track.coverPath, 520)
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.94f)
                .clip(RoundedCornerShape(16.dp))
                .background(Surface1)
        ) {
            if (cover != null) {
                Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                CoverPlaceholder(Modifier.fillMaxSize())
                Icon(
                    Icons.Rounded.MusicNote, null,
                    tint = Lav.copy(alpha = 0.42f),
                    modifier = Modifier.align(Alignment.Center).size(38.dp)
                )
            }
            if (isCurrent) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Pink.copy(alpha = 0.92f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (isPlaying) "播放中" else "已暂停",
                        color = Color(0xFF2A0E1A),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            track.title,
            color = TextMain,
            fontSize = 13.5.sp,
            lineHeight = 18.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            buildString {
                append(Fmt.short(track.durationMs))
                if (track.hasSub) append(" · ${track.subFormat.label}")
                if (track.lastPosMs > 5000) append(" · 上次 ${Fmt.short(track.lastPosMs)}")
            },
            color = TextDim,
            fontSize = 11.sp
        )
    }
}


@Composable
private fun EmptySearch() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("没有匹配的结果", color = TextMain, fontSize = 15.sp)
    }
}

@Composable
private fun EmptyState(onPickFiles: () -> Unit, onPickFolder: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(Surface1),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = Lav.copy(alpha = 0.7f), modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "还没有音声",
            color = TextMain,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "选一个文件夹导入，就会像专辑一样分组；\n音频 + 同名封面 + 同名字幕会自动配对，\n文件夹里的 cover.jpg / folder.jpg 也会当封面。",
            color = TextDim,
            fontSize = 12.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("选择文件", Icons.Rounded.FolderOpen, onClick = onPickFiles)
            PillButton("选择文件夹", Icons.Rounded.CreateNewFolder, onClick = onPickFolder)
        }
    }
}
