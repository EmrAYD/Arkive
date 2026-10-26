package com.eayd.arkive.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Yapay zekâ düğmesinin simgesi: yüzeyleri çizili bir elmas ve sağ üstünde bir parıltı. Favori yıldızı gibi iki hâli var:
 *  - [Outlined] (kapalı): yalnızca çizgiler; elmas, yüzey çizgileri ve parıltının dış hattı.
 *  - [Filled] (açık): elmasın yüzeyleri neredeyse tam dolu (yalnızca üst yüzey ve orta yüzey biraz açık bırakılır), parıltı tamamen dolu.
 * Rengi `Icon(tint = …)` belirler; buradaki siyah yalnızca şekli (ve yüzeylerin saydamlığını) çizer.
 */
object AiIcons {
    val Outlined: ImageVector by lazy { build(filled = false) }
    val Filled: ImageVector by lazy { build(filled = true) }

    private fun build(filled: Boolean): ImageVector {
        val ink = SolidColor(Color.Black)
        return ImageVector.Builder(
            name = if (filled) "AiFilled" else "AiOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            if (filled) {
                // Üst yüzey
                path(fill = ink, fillAlpha = 0.6f) {
                    moveTo(5.2f, 2.8f)
                    lineTo(14.6f, 2.8f)
                    lineTo(13.5f, 9.2f)
                    lineTo(6.3f, 9.2f)
                    close()
                }
                // Taç solu
                path(fill = ink, fillAlpha = 0.82f) {
                    moveTo(1.3f, 9.2f)
                    lineTo(5.2f, 2.8f)
                    lineTo(6.3f, 9.2f)
                    close()
                }
                // Taç sağı
                path(fill = ink, fillAlpha = 0.82f) {
                    moveTo(14.6f, 2.8f)
                    lineTo(18.5f, 9.2f)
                    lineTo(13.5f, 9.2f)
                    close()
                }
                // Alt kısım solu
                path(fill = ink, fillAlpha = 1f) {
                    moveTo(1.3f, 9.2f)
                    lineTo(6.3f, 9.2f)
                    lineTo(9.9f, 20.8f)
                    close()
                }
                // Alt kısım ortası
                path(fill = ink, fillAlpha = 0.78f) {
                    moveTo(6.3f, 9.2f)
                    lineTo(13.5f, 9.2f)
                    lineTo(9.9f, 20.8f)
                    close()
                }
                // Alt kısım sağı
                path(fill = ink, fillAlpha = 1f) {
                    moveTo(13.5f, 9.2f)
                    lineTo(18.5f, 9.2f)
                    lineTo(9.9f, 20.8f)
                    close()
                }
            }

            // Elmasın dış hattı
            path(
                stroke = ink,
                strokeLineWidth = 1.5f,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(5.2f, 2.8f)
                lineTo(14.6f, 2.8f)
                lineTo(18.5f, 9.2f)
                lineTo(9.9f, 20.8f)
                lineTo(1.3f, 9.2f)
                close()
            }

            // Yüzey çizgileri (kuşak, taç ve alt kısım)
            path(
                stroke = ink,
                strokeLineWidth = if (filled) 0.9f else 1.2f,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(1.3f, 9.2f)
                lineTo(18.5f, 9.2f)
                moveTo(5.2f, 2.8f)
                lineTo(6.3f, 9.2f)
                moveTo(14.6f, 2.8f)
                lineTo(13.5f, 9.2f)
                moveTo(6.3f, 9.2f)
                lineTo(9.9f, 20.8f)
                moveTo(13.5f, 9.2f)
                lineTo(9.9f, 20.8f)
            }

            // Sağ üstteki dört köşeli parıltı
            if (filled) {
                path(
                    fill = ink,
                    stroke = ink,
                    strokeLineWidth = 0.6f,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(20.2f, 0.7f)
                    curveTo(20.552f, 3.26f, 20.84f, 3.548f, 23.4f, 3.9f)
                    curveTo(20.84f, 4.252f, 20.552f, 4.54f, 20.2f, 7.1f)
                    curveTo(19.848f, 4.54f, 19.56f, 4.252f, 17f, 3.9f)
                    curveTo(19.56f, 3.548f, 19.848f, 3.26f, 20.2f, 0.7f)
                    close()
                }
            } else {
                path(
                    stroke = ink,
                    strokeLineWidth = 1.25f,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(20.2f, 0.7f)
                    curveTo(20.7984f, 2.812f, 21.288f, 3.3016f, 23.4f, 3.9f)
                    curveTo(21.288f, 4.4984f, 20.7984f, 4.988f, 20.2f, 7.1f)
                    curveTo(19.6016f, 4.988f, 19.112f, 4.4984f, 17f, 3.9f)
                    curveTo(19.112f, 3.3016f, 19.6016f, 2.812f, 20.2f, 0.7f)
                    close()
                }
            }
        }.build()
    }
}
