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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.eayd.arkive.utils.FileKind
import com.eayd.arkive.utils.fileKind

// Klasör ve dosya satırlarının ortak görselleri. Liste, çöp kutusu, "Taşı" penceresi ve sürükleme kartı
// aynı simgeleri bu dosyadan alır.

/** "#RRGGBB" metnini Compose rengine çevirir; null ya da hatalıysa null döner. */
fun folderColorOrNull(hex: String?): Color? =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

/** Klasörün simge rengi: seçilmiş renk, yoksa tema rengi. */
@Composable
fun folderAccent(hex: String?): Color = folderColorOrNull(hex) ?: MaterialTheme.colorScheme.primary

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
