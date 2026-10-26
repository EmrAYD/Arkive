package com.eayd.arkive.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eayd.arkive.viewmodel.MoveFolderViewModel

/**
 * "Taşı" penceresi (dosya ve klasörler için): klasör ağacında gezip hedef klasörü seçtirir. En üstte "Ana Sayfa"
 * (kök) vardır. Yalnızca "Ana Sayfa" altındaki klasörler listelenir; AI sonuçları klasörlerine dosya konulamaz.
 * Gezinti durumunu [MoveFolderViewModel] tutar; pencere her açıldığında en üstten başlar.
 *
 * @param summary taşınanları anlatan satır (ör. “rapor.pdf” ya da 3 öğe)
 * @param currentParentIds taşınanların şu anki klasörleri (null = ana sayfanın en üstü); hepsi hedefteyse taşıma anlamsız
 * @param excludedFolderIds listede gösterilmeyecek klasörler (taşınan klasörlerin kendisi)
 * @param onMove hedef klasörün kimliği (null = Ana Sayfa'nın en üstü) ve görünen adı.
 */
@Composable
fun MoveFolderDialog(
    summary: String,
    currentParentIds: List<String?>,
    excludedFolderIds: Set<String>,
    moving: Boolean,
    error: String?,
    onMove: (targetFolderId: String?, targetName: String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: MoveFolderViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.start() }
    // Pencere kapanınca gezinti sıfırlanır: bir sonraki açılışta eski liste bir an görünmesin.
    DisposableEffect(viewModel) { onDispose { viewModel.clear() } }

    // Seçilenler zaten bu klasördeyse "Buraya taşı" anlamsızdır.
    val alreadyHere = currentParentIds.all { it == state.currentId }

    AlertDialog(
        onDismissRequest = { if (!moving) onDismiss() },
        title = { Text("Taşı") },
        text = {
            Column {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.breadcrumb,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))

                if (state.path.isNotEmpty()) {
                    PickerRow(
                        enabled = !moving,
                        onClick = viewModel::up,
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = "Üst klasör",
                    )
                }

                Box(Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 260.dp)) {
                    val list = state.folders
                    val loadError = state.error
                    when {
                        loadError != null -> ErrorRetry(loadError, viewModel::retry, Modifier.align(Alignment.Center))

                        list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                        list.isEmpty() -> Text(
                            "Bu klasörde alt klasör yok.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.align(Alignment.Center),
                        )

                        else -> LazyColumn {
                            // Taşınan klasör kendi içine taşınamaz: listede hiç gösterilmez.
                            items(list.filter { it.id !in excludedFolderIds }, key = { it.id }) { folder ->
                                PickerRow(
                                    enabled = !moving,
                                    onClick = { viewModel.open(folder) },
                                    icon = { FolderIcon(folderAccent(folder.color), folder.emoji, 28.dp) },
                                    label = folder.name,
                                )
                            }
                        }
                    }
                }

                if (alreadyHere) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Seçilenler zaten bu klasörde.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !moving && !alreadyHere,
                onClick = { onMove(state.currentId, state.currentName) },
            ) { Text("Buraya taşı") }
        },
        dismissButton = { TextButton(enabled = !moving, onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun PickerRow(
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
