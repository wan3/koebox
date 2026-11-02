package com.koebox.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koebox.app.data.Fmt
import com.koebox.app.data.Track

/** 底部迷你播放条 */
@Composable
fun MiniPlayerBar(
    track: Track,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cover = rememberCover(track.coverPath, 160)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface2)
            .border(1.dp, Lav.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .clickable { onOpen() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            if (cover != null) {
                Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp))
            } else {
                CoverPlaceholder(Modifier.size(44.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = TextMain,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (isPlaying) "正在播放 · 点开看字幕" else "已暂停",
                color = TextDim,
                fontSize = 10.sp
            )
        }
        IconButton(onClick = onToggle) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = TextMain)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Rounded.SkipNext, null, tint = TextMain)
        }
    }
}

/** 专辑里的单曲行 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    track: Track,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) Lav.copy(alpha = 0.10f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    null, tint = Lav, modifier = Modifier.size(15.dp)
                )
            } else {
                Text(
                    "${index + 1}",
                    color = TextDim.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
        val cover = rememberCover(track.coverPath, 200)
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(9.dp))
        ) {
            if (cover != null) {
                Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.size(42.dp))
            } else {
                CoverPlaceholder(Modifier.size(42.dp))
                Icon(
                    Icons.Rounded.MusicNote, null,
                    tint = Lav.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.Center).size(16.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (isCurrent) TextMain else TextMain.copy(alpha = 0.92f),
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.short(track.durationMs), color = TextDim, fontSize = 10.sp)
                if (track.hasSub) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Subtitles, null, tint = Lav.copy(alpha = 0.8f), modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(track.subFormat.label, color = Lav.copy(alpha = 0.8f), fontSize = 9.sp)
                }
            }
        }
    }
}

/** 长按单曲的操作菜单 */
@Composable
fun TrackActionDialog(
    track: Track,
    onDismiss: () -> Unit,
    onAttachSub: () -> Unit,
    onAttachCover: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        titleContentColor = TextMain,
        textContentColor = TextDim,
        title = {
            Text(track.title, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column {
                DialogRow("选择字幕文件（LRC/SRT/VTT/ASS）") { onAttachSub() }
                DialogRow("选择封面图片") { onAttachCover() }
                DialogRow("删除这条音轨") { onDelete() }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = Lav) }
        }
    )
}

@Composable
fun DialogRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = TextMain, fontSize = 14.sp)
    }
}

/** 小胶囊按钮 */
@Composable
fun PillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, Lav.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Lav, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, color = TextMain, fontSize = 13.sp)
    }
}

/** 「专辑 / 全部」切换 */
@Composable
fun SegmentedSwitch(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Surface1)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (active) Surface2 else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    label,
                    color = if (active) Lav else TextDim,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/** 无封面提示 */
@Composable
fun NoCoverHint(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Image, null, tint = TextDim, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        Text("没有封面，点封面可添加", color = TextDim, fontSize = 10.sp)
    }
}

/** 空状态占位 */
@Composable
fun EmptyStroke(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.dp)
    )
}
