package com.koebox.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ---------------- 配色 ----------------
val Ink = Color(0xFF0B0910)
val Surface1 = Color(0xFF171223)
val Surface2 = Color(0xFF221A33)
val Lav = Color(0xFFC9A7FF)
val Pink = Color(0xFFFF9DC0)
val TextMain = Color(0xFFEFE9FA)
val TextDim = Color(0xFF9A8FB0)

private val DarkColors = darkColorScheme(
    primary = Lav,
    onPrimary = Color(0xFF170F24),
    secondary = Pink,
    onSecondary = Color(0xFF2A0E1A),
    background = Ink,
    onBackground = TextMain,
    surface = Surface1,
    onSurface = TextMain,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextDim
)

@Composable
fun KoeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}

// ---------------- 封面解码 ----------------
private fun decodeCover(path: String?, req: Int): ImageBitmap? {
    if (path == null) return null
    return try {
        val f = File(path)
        if (!f.exists() || f.length() == 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > req * 2 && sample < 8) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
    } catch (e: Throwable) {
        null
    }
}

@Composable
fun rememberCover(path: String?, req: Int = 480): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(initialValue = null, path, req) {
        value = withContext(Dispatchers.IO) { decodeCover(path, req) }
    }
    return bmp
}

/** 封面占位图 */
@Composable
fun CoverPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(Color(0xFF2C2144), Color(0xFF161022)))
        )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.34f)
                .height(1.dp)
                .background(Lav.copy(alpha = 0.35f))
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TextDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        modifier = modifier
    )
}

/** 简易底部面板 */
@Composable
fun SimpleSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!visible) return
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.001f))
    ) {}
    androidx.compose.material3.Surface(
        color = Surface1,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 0.dp)
    ) {
        Column(Modifier.padding(vertical = 8.dp)) { content() }
    }
}

@Composable
fun SheetHandle() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .height(4.dp)
                .fillMaxWidth(0.12f)
                .clip(RoundedCornerShape(2.dp))
                .background(TextDim.copy(alpha = 0.5f))
        )
    }
    Spacer(Modifier.height(4.dp))
}
