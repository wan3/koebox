package com.koebox.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koebox.app.data.Cue
import com.koebox.app.data.Fmt
import com.koebox.app.data.SubtitleParser
import com.koebox.app.data.Track
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class Sheet { NONE, SUBS, LIST, MORE }

@Composable
fun PlayerScreen(
    vm: MainViewModel,
    controller: MediaController,
    ui: PlaybackUi,
    track: Track,
    queue: List<Track>,
    onBack: () -> Unit,
    onPickTrack: (Int) -> Unit,
    onPickCover: () -> Unit,
    onPickSub: () -> Unit
) {
    val prefs = vm.prefs
    var offsetMs by remember { mutableStateOf(prefs.subOffsetMs) }
    var fontSp by remember { mutableStateOf(prefs.subFontSp) }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var dragValue by remember { mutableStateOf<Float?>(null) }
    var sleepUntil by remember { mutableStateOf(0L) }
    var sleepTick by remember { mutableStateOf(0L) }

    // 返回手势 / 返回键：先关面板，再退出播放页（不要退出 App）
    BackHandler(enabled = sheet != Sheet.NONE) { sheet = Sheet.NONE }
    BackHandler(enabled = sheet == Sheet.NONE) {
        vm.savePosition(track.id, ui.position)
        onBack()
    }

    val cues by produceState<List<Cue>>(emptyList(), track.subPath) {
        value = if (track.subPath != null) {
            withContext(Dispatchers.IO) { vm.repo.subtitleOf(track) }
        } else emptyList()
    }

    val cover = rememberCover(track.coverPath, 900)
    val pos = ui.position
    val effPos = pos - offsetMs
    val cueIndex = if (cues.isEmpty()) -1 else SubtitleParser.indexAtHold(cues, effPos)
    val currentCue = if (cueIndex in cues.indices) cues[cueIndex] else null
    val prevCue = if (cueIndex - 1 in cues.indices) cues[cueIndex - 1] else null
    val nextCue = if (cueIndex + 1 in cues.indices) cues[cueIndex + 1] else null

    // 睡眠定时
    LaunchedEffect(sleepUntil) {
        while (sleepUntil > 0L) {
            sleepTick = System.currentTimeMillis()
            if (sleepTick >= sleepUntil) {
                controller.pause()
                sleepUntil = 0L
                break
            }
            delay(1000)
        }
    }
    val remainMin = if (sleepUntil > 0L) {
        (((sleepUntil - sleepTick) / 1000) / 60).coerceAtLeast(0)
    } else 0L

    val dur = ui.duration.coerceAtLeast(1L)
    val shownPos = (dragValue ?: pos.toFloat()).toLong()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val coverSize = minOf(maxWidth - 76.dp, maxHeight * 0.33f)

        // 不透明底色，避免后面的资料库页面透出来
        Box(Modifier.fillMaxSize().background(Ink))

        // 背景
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.32f,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(48.dp)
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Ink.copy(alpha = 0.30f),
                            Ink.copy(alpha = 0.80f),
                            Ink
                        )
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
        ) {
            // 顶栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    vm.savePosition(track.id, pos)
                    onBack()
                }) {
                    Icon(Icons.Rounded.ArrowBackIosNew, null, tint = TextMain, modifier = Modifier.size(19.dp))
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("正在播放", color = TextDim, fontSize = 10.sp, letterSpacing = 1.sp)
                    Text(
                        track.title,
                        color = TextMain,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { sheet = Sheet.SUBS }) {
                    Icon(Icons.Rounded.Subtitles, null, tint = TextDim, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { sheet = Sheet.MORE }) {
                    Icon(Icons.Rounded.MoreHoriz, null, tint = TextDim, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // 封面
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(coverSize)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Surface1)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(26.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (cover == null) onPickCover() else sheet = Sheet.MORE
                    }
            ) {
                if (cover != null) {
                    Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    CoverPlaceholder(Modifier.fillMaxSize())
                    Icon(
                        Icons.Rounded.MusicNote, null,
                        tint = Lav.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                    )
                    Text(
                        "点这里添加封面",
                        color = TextDim,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // 字幕区
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 104.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (cues.isNotEmpty()) sheet = Sheet.SUBS
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                if (cues.isEmpty()) {
                    Text(
                        "这条音轨还没有字幕\n长按封面卡片可以指定字幕文件",
                        color = TextDim.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            prevCue?.text ?: " ",
                            color = TextDim.copy(alpha = 0.45f),
                            fontSize = (fontSp * 0.85f).sp,
                            lineHeight = (fontSp * 1.3f).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            currentCue?.text ?: (if (cues.isNotEmpty()) "♪" else ""),
                            color = if (currentCue != null) TextMain else TextDim.copy(alpha = 0.25f),
                            fontSize = fontSp.sp,
                            lineHeight = (fontSp * 1.35f).sp,
                            fontWeight = if (currentCue != null) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            nextCue?.text ?: " ",
                            color = TextDim.copy(alpha = 0.45f),
                            fontSize = (fontSp * 0.85f).sp,
                            lineHeight = (fontSp * 1.3f).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // 进度
            Slider(
                value = (dragValue ?: pos.toFloat()).coerceIn(0f, dur.toFloat()),
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { controller.seekTo(it.toLong()) }
                    dragValue = null
                },
                valueRange = 0f..dur.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Lav,
                    activeTrackColor = Lav,
                    inactiveTrackColor = Surface2,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                Text(Fmt.time(shownPos), color = TextDim, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(Fmt.time(dur), color = TextDim, fontSize = 11.sp)
            }

            Spacer(Modifier.height(6.dp))

            // 控制
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { controller.seekTo((pos - 10000L).coerceAtLeast(0L)) }) {
                    Icon(Icons.Rounded.Replay10, null, tint = TextMain.copy(alpha = 0.85f), modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = { controller.seekToPreviousMediaItem() }) {
                    Icon(Icons.Rounded.SkipPrevious, null, tint = TextMain, modifier = Modifier.size(34.dp))
                }
                Box(
                    Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Lav, Pink)))
                        .clickable {
                            if (controller.isPlaying) controller.pause() else controller.play()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        tint = Color(0xFF1A1024),
                        modifier = Modifier.size(38.dp)
                    )
                }
                IconButton(onClick = { controller.seekToNextMediaItem() }) {
                    Icon(Icons.Rounded.SkipNext, null, tint = TextMain, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { controller.seekTo((pos + 10000L).coerceAtMost(dur)) }) {
                    Icon(Icons.Rounded.Forward10, null, tint = TextMain.copy(alpha = 0.85f), modifier = Modifier.size(30.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // 功能胶囊
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallChip("${fmtSpeed(ui.speed)}x") { sheet = Sheet.MORE }
                SmallChip("字幕 ${fmtOffset(offsetMs)}") {
                    offsetMs = 0L
                    prefs.subOffsetMs = 0L
                }
                SmallChip(if (sleepUntil > 0L) "睡眠 ${remainMin}分" else "睡眠", Icons.Rounded.Bedtime) {
                    sheet = Sheet.MORE
                }
                SmallChip("列表", Icons.Rounded.QueueMusic) { sheet = Sheet.LIST }
            }

            Spacer(Modifier.height(14.dp))
        }

        // ---------------- 面板 ----------------
        if (sheet != Sheet.NONE) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { sheet = Sheet.NONE }
            )
            Surface(
                color = Surface1,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(width = 40.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(TextDim.copy(alpha = 0.4f))
                        )
                    }

                    when (sheet) {
                        Sheet.SUBS -> SubtitlePanel(
                            cues = cues,
                            cueIndex = cueIndex,
                            offsetMs = offsetMs,
                            fontSp = fontSp,
                            onSeek = { ms -> controller.seekTo((ms + offsetMs).coerceAtLeast(0L)) },
                            onOffset = { d ->
                                offsetMs = (offsetMs + d).coerceIn(-30000L, 30000L)
                                prefs.subOffsetMs = offsetMs
                            },
                            onFont = { d ->
                                fontSp = (fontSp + d).coerceIn(12f, 30f)
                                prefs.subFontSp = fontSp
                            }
                        )

                        Sheet.LIST -> PlaylistPanel(
                            tracks = if (queue.isEmpty()) vm.tracks.toList() else queue,
                            currentId = track.id,
                            onPick = { i -> onPickTrack(i); sheet = Sheet.NONE }
                        )

                        Sheet.MORE -> MorePanel(
                            speed = ui.speed,
                            onSpeed = { s -> controller.setPlaybackSpeed(s); prefs.speed = s },
                            offsetMs = offsetMs,
                            onOffset = { d ->
                                offsetMs = (offsetMs + d).coerceIn(-30000L, 30000L)
                                prefs.subOffsetMs = offsetMs
                            },
                            onSleep = { min ->
                                sleepUntil = if (min <= 0) 0L else System.currentTimeMillis() + min * 60_000L
                                sleepTick = System.currentTimeMillis()
                                sheet = Sheet.NONE
                            },
                            sleepActive = sleepUntil > 0L,
                            onPickCover = { sheet = Sheet.NONE; onPickCover() },
                            onPickSub = { sheet = Sheet.NONE; onPickSub() },
                            floating = vm.floatingSub,
                            onFloating = { vm.setFloating(it) }
                        )

                        else -> {}
                    }
                }
            }
        }
    }
}

private fun fmtSpeed(s: Float): String =
    if (s == s.toInt().toFloat()) "${s.toInt()}.0" else String.format("%.2f", s).trimEnd('0').trimEnd('.')

private fun fmtOffset(ms: Long): String {
    if (ms == 0L) return "0.0"
    val sign = if (ms > 0) "+" else "-"
    return "$sign${String.format("%.1f", kotlin.math.abs(ms) / 1000f)}s"
}

@Composable
private fun SmallChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = TextDim, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = TextMain.copy(alpha = 0.9f), fontSize = 11.sp)
    }
}

@Composable
private fun SubtitlePanel(
    cues: List<Cue>,
    cueIndex: Int,
    offsetMs: Long,
    fontSp: Float,
    onSeek: (Long) -> Unit,
    onOffset: (Long) -> Unit,
    onFont: (Float) -> Unit
) {
    val state = rememberLazyListState()
    LaunchedEffect(cueIndex) {
        if (cueIndex >= 0) {
            state.scrollToItem((cueIndex - 2).coerceAtLeast(0))
        }
    }
    Column(Modifier.height(420.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("字幕", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("  ${cues.size} 条", color = TextDim, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            ChipAction("-0.5s") { onOffset(-500) }
            ChipAction("+0.5s") { onOffset(500) }
            ChipAction("A-") { onFont(-1f) }
            ChipAction("A+") { onFont(1f) }
        }
        if (cues.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                Text(
                    "没有读到字幕。可以在资料页长按卡片，手动指定字幕文件。",
                    color = TextDim,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
            return@Column
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(cues) { index, cue ->
                val active = index == cueIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) Lav.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onSeek(cue.startMs) }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(
                        Fmt.short(cue.startMs),
                        color = if (active) Lav else TextDim.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        modifier = Modifier.width(44.dp).padding(top = 2.dp)
                    )
                    Text(
                        cue.text,
                        color = if (active) TextMain else TextDim,
                        fontSize = (fontSp * 0.78f).sp,
                        lineHeight = (fontSp * 1.15f).sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Lav,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Surface2)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
    Spacer(Modifier.width(6.dp))
}

@Composable
private fun PlaylistPanel(tracks: List<Track>, currentId: String, onPick: (Int) -> Unit) {
    Column(Modifier.height(420.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("播放列表", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("  ${tracks.size}", color = TextDim, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(tracks) { index, t ->
                val active = t.id == currentId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) Lav.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onPick(index) }
                        .padding(horizontal = 8.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${index + 1}",
                        color = if (active) Lav else TextDim.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        modifier = Modifier.width(26.dp)
                    )
                    Text(
                        t.title,
                        color = if (active) TextMain else TextDim,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (active) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Lav, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MorePanel(
    speed: Float,
    onSpeed: (Float) -> Unit,
    offsetMs: Long,
    onOffset: (Long) -> Unit,
    onSleep: (Int) -> Unit,
    sleepActive: Boolean,
    onPickCover: () -> Unit,
    onPickSub: () -> Unit,
    floating: Boolean,
    onFloating: (Boolean) -> Unit
) {
    val speeds = listOf(0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)
    Column {
        Text("语速", color = TextDim, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            speeds.take(4).forEach { s -> SpeedChip(s, speed, onSpeed) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            speeds.drop(4).forEach { s -> SpeedChip(s, speed, onSpeed) }
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("字幕偏移", color = TextDim, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.width(10.dp))
            Text(fmtOffset(offsetMs), color = Lav, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            ChipAction("-1s") { onOffset(-1000) }
            ChipAction("-0.2s") { onOffset(-200) }
            ChipAction("+0.2s") { onOffset(200) }
            ChipAction("+1s") { onOffset(1000) }
        }

        Spacer(Modifier.height(18.dp))
        Text("睡眠定时", color = TextDim, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SleepChip("关闭", sleepActive) { onSleep(0) }
            SleepChip("10分", false) { onSleep(10) }
            SleepChip("20分", false) { onSleep(20) }
            SleepChip("30分", false) { onSleep(30) }
            SleepChip("60分", false) { onSleep(60) }
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("悬浮字幕", color = TextMain.copy(alpha = 0.9f), fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    "播放时在资料库、专辑页也能看到当前字幕",
                    color = TextDim,
                    fontSize = 10.sp
                )
            }
            androidx.compose.material3.Switch(
                checked = floating,
                onCheckedChange = onFloating,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF1A1024),
                    checkedTrackColor = Lav,
                    uncheckedThumbColor = TextDim,
                    uncheckedTrackColor = Surface2
                )
            )
        }

        Spacer(Modifier.height(18.dp))
        Text("这个音轨", color = TextDim, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SleepChip("选择封面", false, onPickCover)
            SleepChip("选择字幕", false, onPickSub)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SpeedChip(s: Float, current: Float, onSpeed: (Float) -> Unit) {
    val active = kotlin.math.abs(s - current) < 0.001f
    Text(
        "${fmtSpeed(s)}x",
        color = if (active) Color(0xFF1A1024) else TextMain.copy(alpha = 0.85f),
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Lav else Surface2)
            .clickable { onSpeed(s) }
            .padding(horizontal = 13.dp, vertical = 8.dp)
    )
}

@Composable
private fun SleepChip(text: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Lav.copy(alpha = 0.2f) else Surface2)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (active) {
            Icon(Icons.Rounded.Check, null, tint = Lav, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, color = if (active) Lav else TextMain.copy(alpha = 0.85f), fontSize = 12.sp)
    }
}
