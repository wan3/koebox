package com.koebox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koebox.app.data.Fmt
import com.koebox.app.data.ListeningSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 顶栏里的计时胶囊：关闭时只显示图标 */
@Composable
fun TimerPill(vm: MainViewModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val on = vm.timerEnabled
    Row(
        modifier
            .clip(CircleShape)
            .background(if (on) Surface2 else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Timer, null,
            tint = if (on) Lav else TextDim,
            modifier = Modifier.size(16.dp)
        )
        if (on) {
            Spacer(Modifier.width(5.dp))
            Text(
                Fmt.clock(vm.timerElapsed),
                color = if (vm.timerRunning) TextDim else TextMain,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/** 计时器面板 */
@Composable
fun TimerSheetContent(
    vm: MainViewModel,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Surface1)
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
    ) {
        Box(
            Modifier
                .padding(top = 10.dp, bottom = 14.dp)
                .width(46.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Surface2)
                .align(Alignment.CenterHorizontally)
        )

        Row(
            Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Timer, null,
                tint = Lav,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                Fmt.clock(vm.timerElapsed),
                color = TextMain,
                fontSize = 30.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.padding(horizontal = 20.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(Lav)
                    .clickable { vm.toggleTimerRunning() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (vm.timerRunning) "继续" else "暂停",
                    color = Color(0xFF1A1024),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(Surface2)
                    .clickable { vm.resetTimer() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("归零", color = TextDim, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Surface2).padding(horizontal = 20.dp))

        SwitchRow(
            title = "开启计时器",
            subtitle = "在顶部显示计时",
            checked = vm.timerEnabled,
            onChange = { vm.enableTimer(it) }
        )
        SwitchRow(
            title = "只在播放音频时计时",
            subtitle = "关闭后，App 打开期间一直走",
            checked = vm.timerOnlyPlaying,
            onChange = { vm.setOnlyPlaying(it) }
        )

        Box(Modifier.fillMaxWidth().height(1.dp).background(Surface2))

        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onHistory() }
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("最近一周", color = TextMain, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                val (count, total) = weekSummary(vm.sessions)
                Text(
                    if (count == 0) "还没有记录" else "$count 次 · 共 ${Fmt.human(total)}",
                    color = TextDim,
                    fontSize = 11.sp
                )
            }
            Text("›", color = TextDim, fontSize = 20.sp)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Surface2))

        Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
            Text("计数规则", color = Lav, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            listOf(
                "正计时，记录这次听了多久",
                "退到桌面、锁屏继续走（按真实时间算）",
                "换专辑或归零时，自动记一条，保留 7 天"
            ).forEach {
                Text("· $it", color = TextDim, fontSize = 11.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = TextDim, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF1A1024),
                checkedTrackColor = Lav,
                uncheckedThumbColor = TextDim,
                uncheckedTrackColor = Surface2
            )
        )
    }
}

private fun weekSummary(list: List<ListeningSession>): Pair<Int, Long> {
    val cutoff = todayStart() - 6L * 24 * 3600 * 1000
    val recent = list.filter { it.startAt >= cutoff }
    return recent.size to recent.sumOf { it.durationMs }
}

private fun todayStart(): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** 收听记录页 */
@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit) {
    val list = vm.sessions
    val cutoff = todayStart() - 6L * 24 * 3600 * 1000
    val recent = list.filter { it.startAt >= cutoff }
    val grouped = recent.groupBy { dayKey(it.startAt) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable { onBack() }
                    .padding(10.dp)
            ) {
                Icon(
                    Icons.Rounded.ArrowBackIosNew, null,
                    tint = TextMain,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text("收听记录", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text("本周累计", color = TextDim, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Fmt.human(recent.sumOf { it.durationMs }),
                    color = TextMain,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(10.dp))
                Text("${recent.size} 次", color = Lav, fontSize = 13.sp)
            }
        }

        if (recent.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("最近一周还没有记录", color = TextDim, fontSize = 13.sp)
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            grouped.keys.sortedDescending().forEach { key ->
                item(key = "h$key") {
                    Text(
                        dayLabel(key),
                        color = Lav,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 4.dp)
                    )
                }
                items(grouped[key].orEmpty(), key = { it.id }) { s ->
                    SessionRow(s)
                }
            }
            item {
                Text(
                    "超过 7 天的记录会自动清理",
                    color = TextDim.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 6.dp, top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun SessionRow(s: ListeningSession) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                timeRange(s.startAt, s.endAt),
                color = TextDim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(4.dp))
            Text(
                s.album.ifBlank { "未分类" },
                color = TextMain,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Text(
            Fmt.human(s.durationMs),
            color = Lav,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun dayKey(ms: Long): String =
    SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(ms))

private fun dayLabel(key: String): String {
    val today = dayKey(System.currentTimeMillis())
    val yesterday = dayKey(System.currentTimeMillis() - 24 * 3600 * 1000)
    return when (key) {
        today -> "今天"
        yesterday -> "昨天"
        else -> key.substring(4, 6).trimStart('0') + "月" +
            key.substring(6, 8).trimStart('0') + "日"
    }
}

private fun timeRange(start: Long, end: Long): String {
    val f = SimpleDateFormat("HH:mm", Locale.getDefault())
    return "${f.format(Date(start))} – ${f.format(Date(end))}"
}
