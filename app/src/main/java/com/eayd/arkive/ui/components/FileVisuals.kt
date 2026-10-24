package com.eayd.arkive.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.eayd.arkive.utils.FileKind
import com.eayd.arkive.utils.fileKind

// Klasör ve dosya satırlarının ortak görselleri. Liste, çöp kutusu, "Taşı" penceresi ve sürükleme kartı
// aynı simgeleri bu dosyadan alır.

/** "#RRGGBB" metnini Compose rengine çevirir; null ya da hatalıysa null döner. */
fun folderColorOrNull(hex: String?): Color? =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

/** Bir klasör renginin açık ve koyu temadaki görünümü. */
private class FolderTone(val light: Color, val dark: Color)

/**
 * Renk seçicideki her renk için temaya göre elle seçilmiş ton çifti. Sunucuda hep seçilen özgün renk
 * ("#RRGGBB") saklanır; ekranda gösterilirken temaya göre bu çiftten biri kullanılır:
 * açık temada (açık zemin) daha KOYU, koyu temada (koyu zemin) daha AÇIK ton. Anahtarlar büyük harfle yazılır.
 */
private val FolderTones: Map<String, FolderTone> = mapOf(
    "#1E88E5" to FolderTone(light = Color(0xFF1879CE), dark = Color(0xFF5FAAED)),   // mavi
    "#43A047" to FolderTone(light = Color(0xFF44A248), dark = Color(0xFF81CA84)),   // yeşil
    "#FB8C00" to FolderTone(light = Color(0xFFE68000), dark = Color(0xFFFFB04D)),   // turuncu
    "#E53935" to FolderTone(light = Color(0xFFCB1E1A), dark = Color(0xFFEB6461)),   // kırmızı
    "#8E24AA" to FolderTone(light = Color(0xFF9E28BD), dark = Color(0xFFC86CE0)),   // mor
    "#000000" to FolderTone(light = Color(0xFF000000), dark = Color(0xFFFFFFFF)),   // siyah / beyaz
)

/** Uygulama şu an koyu temada mı (kullanıcının seçtiği tema dahil; zeminin parlaklığına bakılır). */
@Composable
private fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Listede olmayan bir rengi temaya uydurur: açık temada koyulaştırır, koyu temada açar (tonu korur). */
private fun adaptToTheme(color: Color, dark: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[2] = if (dark) maxOf(hsl[2], 0.72f) else minOf(hsl[2], 0.36f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * Klasörün simge rengi: seçilmiş rengin temaya uygun tonu (açık temada koyu, koyu temada açık), renk yoksa tema rengi.
 * Klasör simgesi çizen her yer bunu kullanmalı; ham renk için [folderColorOrNull].
 */
@Composable
fun folderAccent(hex: String?): Color {
    val base = folderColorOrNull(hex) ?: return MaterialTheme.colorScheme.primary
    val dark = isDarkTheme()
    val tone = FolderTones[hex?.uppercase()]
    return when {
        tone != null -> if (dark) tone.dark else tone.light
        else -> adaptToTheme(base, dark)
    }
}

/** Dosya türüne uygun simge. Sınıflandırma [fileKind] ile yapılır; tür adı ve ön izleme de aynısını kullanır. */
fun fileTypeIcon(mimeType: String?): ImageVector = when (fileKind(mimeType)) {
    FileKind.Image -> Icons.Filled.Image
    FileKind.Audio -> Icons.Filled.AudioFile
    FileKind.Video -> Icons.Filled.VideoFile
    FileKind.Pdf -> Icons.Filled.PictureAsPdf
    else -> Icons.Filled.Description
}

/** Basit klasör simgesi (ek simge kütüphanesi gerektirmesin diye elle çizilir). */
@Composable
fun FolderGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 40.dp, height = 32.dp)) {
        val corner = CornerRadius(4.dp.toPx())
        // Sekme (klasörün sol üstündeki çıkıntı)
        drawRoundRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(size.width * 0.45f, size.height * 0.5f),
            cornerRadius = corner,
        )
        // Gövde
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, size.height * 0.15f),
            size = Size(size.width, size.height * 0.85f),
            cornerRadius = corner,
        )
    }
}

/** Dosya simgesi; klasör simgesiyle aynı genişlikte, satırlar alt alta hizalı görünsün. */
@Composable
fun FileGlyph(mimeType: String?) {
    Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = fileTypeIcon(mimeType),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
    }
}
