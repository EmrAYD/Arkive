package com.eayd.arkive.screens.folders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eayd.arkive.viewmodel.BrowserViewModel

/**
 * Bir klasörün içi: üst çubukta geri düğmesi ve klasör adı; altında klasör/dosya listesi ve sağ üstte "+" düğmesi (yeni klasör / dosya yükle).
 * Klasör adı rotadan değil sunucudan alınır (adda "%", "+" gibi karakterler olabilir).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    folderId: String,
    onBack: () -> Unit,
    onOpenFolder: (folderId: String) -> Unit,
    onOpenFile: (fileId: String) -> Unit,
) {
    // key: her klasörün kendi ViewModel'i olsun (bir klasörden diğerine geçince liste karışmasın).
    val viewModel: BrowserViewModel = viewModel(
        key = "folder-$folderId",
        factory = BrowserViewModel.factory(parentId = folderId, location = null),
    )
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopAppBar(
            title = { Text(state.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        BrowserContent(
            viewModel = viewModel,
            onOpenFolder = { onOpenFolder(it.id) },
            onOpenFile = { onOpenFile(it.id) },
        )
    }
}
