package com.eayd.arkive.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Klasör simgesinin emojili hâlleri. Emoji, klasör şeklinin gövdesinin tam ortasına çizilir.

/** Elle çizilen klasör simgesi ([FolderGlyph]) + [emoji] doluysa gövdenin ortasında emoji. */
@Composable
fun FolderGlyph(color: Color, emoji: String?, modifier: Modifier = Modifier) {
    Box(modifier.size(width = 40.dp, height = 32.dp)) {
        FolderGlyph(color)
        if (!emoji.isNullOrEmpty()) {
            // Gövde, yüksekliğin %15'inden aşağısıdır (FolderGlyph'in çizimi); emoji o alanın ortasında durur.
            Box(Modifier.fillMaxSize().padding(top = 32.dp * 0.15f), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 17.sp)
            }
        }
    }
}

/**
 * Hazır klasör simgesi (Material) + [emoji] doluysa ortasında emoji. "Taşı" penceresi ve sürükleme kartı gibi
 * küçük yerlerde kullanılır; [size] simgenin kenarıdır.
 */
@Composable
fun FolderIcon(color: Color, emoji: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Folder, contentDescription = null, tint = color, modifier = Modifier.size(size))
        if (!emoji.isNullOrEmpty()) {
            // Material klasör simgesinin gövdesi biraz aşağıdadır: emoji de aynı oranda aşağı kaydırılır.
            Box(Modifier.size(size).padding(top = size * 0.12f), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = (size.value * 0.46f).sp)
            }
        }
    }
}
