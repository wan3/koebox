package com.koebox.app.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.koebox.app.data.Cue
import com.koebox.app.data.SubtitleParser
import com.koebox.app.data.Track
import com.koebox.app.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** 播放状态的共享视图模型 */
class PlaybackUi {
    var currentId by mutableStateOf<String?>(null)
    var isPlaying by mutableStateOf(false)
    var position by mutableStateOf(0L)
    var duration by mutableStateOf(0L)
    var speed by mutableStateOf(1f)
    var buffering by mutableStateOf(false)
    var queueSize by mutableStateOf(0)
    var currentIndex by mutableStateOf(0)
}

@Composable
fun rememberController(context: Context): MediaController? {
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val executor = ContextCompat.getMainExecutor(context)
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()
        }, executor)
        onDispose {
            MediaController.releaseFuture(future)
        }
    }
    return controller
}

/** 待确认范围的换封面请求 */
private data class PendingCover(val track: Track, val uri: Uri)

@Composable
fun App(vm: MainViewModel) {
    val context = LocalContext.current
    val controller = rememberController(context)
    val ui = remember { PlaybackUi() }
    val snackbar = remember { SnackbarHostState() }

    var playerOpen by remember { mutableStateOf(false) }
    var album by remember { mutableStateOf<String?>(null) }
    var queue by remember { mutableStateOf<List<Track>>(emptyList()) }
    var subTarget by remember { mutableStateOf<Track?>(null) }
    var coverTarget by remember { mutableStateOf<Track?>(null) }
    var pendingCover by remember { mutableStateOf<PendingCover?>(null) }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.importUris(uris) }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) vm.importTree(uri) }

    val pickSub = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val t = subTarget
        subTarget = null
        if (uri != null && t != null) vm.attachSubtitle(t, uri)
    }

    val pickCover = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val t = coverTarget
        coverTarget = null
        if (uri != null && t != null) pendingCover = PendingCover(t, uri)
    }

    // 播放器状态监听
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                ui.isPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                ui.buffering = state == Player.STATE_BUFFERING
                ui.duration = c.duration.coerceAtLeast(0L)
                ui.queueSize = c.mediaItemCount
                ui.currentIndex = c.currentMediaItemIndex
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                ui.currentId = mediaItem?.mediaId
                ui.duration = c.duration.coerceAtLeast(0L)
                ui.currentIndex = c.currentMediaItemIndex
                ui.queueSize = c.mediaItemCount
                ui.position = 0L
            }

            override fun onPlaybackParametersChanged(params: androidx.media3.common.PlaybackParameters) {
                ui.speed = params.speed
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                vm.message = "播放失败：${error.errorCodeName}，可能是文件损坏或格式不支持"
            }
        }
        c.addListener(listener)
        ui.isPlaying = c.isPlaying
        ui.currentId = c.currentMediaItem?.mediaId
        ui.currentIndex = c.currentMediaItemIndex
        ui.queueSize = c.mediaItemCount
        c.setRepeatMode(Player.REPEAT_MODE_ALL)
        onDispose { c.removeListener(listener) }
    }

    // 进度滴答
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        var tick = 0
        while (true) {
            if (c.isPlaying || ui.buffering) {
                ui.position = c.currentPosition
                val d = c.duration
                if (d > 0) ui.duration = d
                tick++
                if (tick % 60 == 0) {
                    val id = c.currentMediaItem?.mediaId
                    if (id != null) vm.savePosition(id, c.currentPosition)
                }
            }
            delay(100)
        }
    }

    LaunchedEffect(vm.message) {
        val m = vm.message
        if (m != null) {
            snackbar.showSnackbar(m)
            vm.message = null
        }
    }

    fun startAt(list: List<Track>, index: Int, pos: Long = -1L) {
        val c = controller
        if (c == null) {
            vm.message = "播放器还没准备好，再点一次试试"
            return
        }
        if (index !in list.indices) return
        val items = list.map { t ->
            MediaItem.Builder()
                .setUri(Uri.fromFile(File(t.audioPath)))
                .setMediaId(t.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.folder.ifBlank { "声匣" })
                        .build()
                )
                .build()
        }
        var startPos = pos
        if (startPos < 0) {
            val t = list[index]
            startPos = if (t.lastPosMs > 5000 && (t.durationMs == 0L || t.lastPosMs < t.durationMs - 4000)) {
                t.lastPosMs
            } else 0L
        }
        queue = list
        c.setMediaItems(items, index, startPos)
        c.prepare()
        c.play()
        ui.currentId = list[index].id
        playerOpen = true
    }

    val currentTrack: Track? = vm.tracks.firstOrNull { it.id == ui.currentId }

    Box(Modifier.fillMaxSize().background(Ink)) {
        val albumName = album
        if (albumName == null) {
            LibraryScreen(
                vm = vm,
                playingId = ui.currentId,
                isPlaying = ui.isPlaying,
                onOpenAlbum = { name -> album = name },
                onPlayList = { list, index -> startAt(list, index) },
                onPickFiles = { pickFiles.launch(arrayOf("*/*")) },
                onPickFolder = { pickFolder.launch(null) },
                onOpenPlayer = { if (currentTrack != null) playerOpen = true },
                onAttachSub = { t -> subTarget = t; pickSub.launch(arrayOf("*/*")) },
                onAttachCover = { t -> coverTarget = t; pickCover.launch(arrayOf("*/*")) },
                onToggle = { controller?.let { c -> if (c.isPlaying) c.pause() else c.play() } },
                onNext = { controller?.seekToNextMediaItem() }
            )
        } else {
            AlbumScreen(
                vm = vm,
                folder = albumName,
                playingId = ui.currentId,
                isPlaying = ui.isPlaying,
                onBack = { album = null },
                onPlay = { list, index -> startAt(list, index) },
                onOpenPlayer = { if (currentTrack != null) playerOpen = true },
                onAttachSub = { t -> subTarget = t; pickSub.launch(arrayOf("*/*")) },
                onAttachCover = { t -> coverTarget = t; pickCover.launch(arrayOf("*/*")) },
                onToggle = { controller?.let { c -> if (c.isPlaying) c.pause() else c.play() } },
                onNext = { controller?.seekToNextMediaItem() }
            )
        }

        AnimatedVisibility(
            visible = playerOpen && controller != null && currentTrack != null,
            enter = slideInVertically(tween(260)) { it },
            exit = slideOutVertically(tween(200)) { it }
        ) {
            if (controller != null && currentTrack != null) {
                PlayerScreen(
                    vm = vm,
                    controller = controller,
                    ui = ui,
                    track = currentTrack,
                    queue = queue,
                    onBack = { playerOpen = false },
                    onPickTrack = { index -> startAt(queue, index, 0L) },
                    onPickCover = { coverTarget = currentTrack; pickCover.launch(arrayOf("*/*")) },
                    onPickSub = { subTarget = currentTrack; pickSub.launch(arrayOf("*/*")) }
                )
            }
        }

        // 悬浮字幕：播放中、不在播放页时显示在底部
        if (vm.floatingSub && !playerOpen && currentTrack != null && currentTrack.hasSub) {
            FloatingSubtitle(
                vm = vm,
                track = currentTrack,
                position = ui.position,
                offsetMs = vm.prefs.subOffsetMs,
                onOpen = { playerOpen = true },
                onClose = { vm.setFloating(false) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 92.dp)
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Surface2,
                contentColor = TextMain
            )
        }
    }

    // 换封面：问一下是改一首还是整张专辑
    val pc = pendingCover
    if (pc != null) {
        val albumName = pc.track.folder.ifBlank { "未分类" }
        val albumCount = vm.tracks.count { it.folder == pc.track.folder }
        AlertDialog(
            onDismissRequest = { pendingCover = null },
            containerColor = Surface1,
            titleContentColor = TextMain,
            textContentColor = TextDim,
            title = { Text("这张封面用在哪？", fontSize = 16.sp) },
            text = {
                Text(
                    "现在是「${pc.track.title}」。也可以把「$albumName」里 $albumCount 首的封面一起换掉。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        vm.attachCover(pc.track, pc.uri)
                        pendingCover = null
                    }) { Text("只改这首", color = Lav) }
                    TextButton(onClick = {
                        vm.attachCoverToAlbum(pc.track.folder, pc.uri)
                        pendingCover = null
                    }) { Text("整张专辑", color = Lav) }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCover = null }) { Text("取消", color = TextDim) }
            }
        )
    }
}

/** 悬浮字幕条：可上下拖动，点一下回到播放页 */
@Composable
private fun FloatingSubtitle(
    vm: MainViewModel,
    track: Track,
    position: Long,
    offsetMs: Long,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cues by produceState(emptyList<Cue>(), track.subPath) {
        value = if (track.subPath != null) {
            withContext(Dispatchers.IO) { vm.repo.subtitleOf(track) }
        } else emptyList()
    }
    var dragY by remember(track.id) { mutableStateOf(0f) }

    val effPos = position - offsetMs
    val idx = if (cues.isEmpty()) -1 else SubtitleParser.indexAtHold(cues, effPos)
    val current = if (idx in cues.indices) cues[idx].text else null
    val prev = if (idx - 1 in cues.indices) cues[idx - 1].text else null

    Box(
        modifier
            .offset { IntOffset(0, dragY.roundToInt()) }
            .pointerInput(track.id) {
                detectVerticalDragGestures { _, d -> dragY += d }
            }
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1.copy(alpha = 0.96f))
            .fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onOpen() }
            ) {
                if (prev != null) {
                    Text(
                        prev,
                        color = TextDim.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(3.dp))
                }
                Text(
                    current ?: "♪",
                    color = if (current != null) TextMain else TextDim.copy(alpha = 0.4f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = if (current != null) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface2)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        "×",
                        color = TextDim,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onClose() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
