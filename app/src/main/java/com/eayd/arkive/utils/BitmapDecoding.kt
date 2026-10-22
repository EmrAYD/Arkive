package com.eayd.arkive.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Görüntüyü, en uzun kenarı en çok [maxDimension] piksel olacak şekilde küçülterek belleğe alır.
 * (12 MP'lik bir fotoğraf tam çözünürlükte ~48 MB tutar; ekranda bundan fazlasına gerek yoktur.)
 * Telefon fotoğraflarındaki EXIF yönü okunur, görüntü dik gösterilir. Çözülemezse (desteklenmeyen biçim) null.
 */
fun decodeScaledBitmap(file: File, maxDimension: Int = 2048): Bitmap? {
    // 1. geçiş: yalnızca boyutları oku (piksel yüklenmez)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    // inSampleSize 2'nin kuvvetidir: 2 => her yönde yarı boyut (4 kat az bellek)
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2

    // 2. geçiş: küçültülmüş olarak gerçekten çöz
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null

    val degrees = runCatching {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    if (degrees == 0f) return bitmap

    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
}
