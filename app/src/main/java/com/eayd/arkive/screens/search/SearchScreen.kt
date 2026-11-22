package com.eayd.arkive.screens.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eayd.arkive.data.SearchHitDto
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.ui.components.CenteredHint
import com.eayd.arkive.ui.components.ErrorRetry
import com.eayd.arkive.ui.components.FileGlyph
import com.eayd.arkive.ui.components.FolderGlyph
import com.eayd.arkive.ui.components.ListSkeleton
import com.eayd.arkive.ui.components.SearchField
import com.eayd.arkive.ui.components.folderAccent
import com.eayd.arkive.utils.SearchText
import com.eayd.arkive.utils.formatBytes
import com.eayd.arkive.viewmodel.SearchMode
import com.eayd.arkive.viewmodel.SearchViewModel

/** Arama ekranı: dosya ve klasör adlarında, dosyaların içinde arar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenFolder: (folderId: String) -> Unit,
    onOpenFile: (fileId: String) -> Unit,
    viewModel: SearchViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (!focusedOnce) {
            focusedOnce = true
            focus.requestFocus()
        } else {
            viewModel.refresh()
        }
    }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) keyboard?.hide()
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Txt.common.back)
            }
            SearchField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                onSearch = {
                    viewModel.searchNow()
                    keyboard?.hide()
                },
                onClear = {
                    viewModel.setQuery("")
                    focus.requestFocus()
                    keyboard?.show()
                },
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp).focusRequester(focus),
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { viewModel.setMode(mode) },
                    label = { Text(mode.label) },
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (state.loading && state.results.isNotEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        HorizontalDivider()

        val error = state.errorMessage
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !SearchText.canSearch(state.query) -> TopHint(Txt.search.prompt)

                error != null -> ErrorRetry(error, onRetry = viewModel::searchNow, modifier = Modifier.align(Alignment.TopCenter))

                state.results.isEmpty() && state.loading -> ListSkeleton(Modifier.fillMaxSize(), rows = 6)

                state.results.isEmpty() -> Column(Modifier.fillMaxWidth()) {
                    TopHint(Txt.search.noResults(state.searched))
                    if (state.mode != SearchMode.Name) NoteText(Txt.search.contentNote)
                }

                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.results, key = { hit -> hit.key }) { hit ->
                        SearchResultRow(
                            hit = hit,
                            onClick = {
                                keyboard?.hide()
                                hit.folder?.let { onOpenFolder(it.id) } ?: hit.file?.let { onOpenFile(it.id) }
                            },
                        )
                    }
                    if (state.mode != SearchMode.Name) {
                        item(key = "note") { NoteText(Txt.search.contentNote) }
                    }
                }
            }
        }
    }
}

private val SearchMode.label: String
    get() = when (this) {
        SearchMode.All -> Txt.search.modeAll
        SearchMode.Name -> Txt.search.modeName
        SearchMode.Content -> Txt.search.modeContent
    }

private val SearchHitDto.key: String
    get() = folder?.let { "folder-${it.id}" } ?: "file-${file?.id}"

/** Bir sonuç: simge, eşleşen yeri vurgulu ad, bulunduğu klasör ve varsa içinden bir parça. */
@Composable
private fun SearchResultRow(hit: SearchHitDto, onClick: () -> Unit) {
    val folder = hit.folder
    val file = hit.file
    val name = folder?.name ?: file?.name.orEmpty()
    val mark = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    val place = SearchText.place(Txt.home.home, hit.path)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (folder != null) FolderGlyph(folderAccent(folder.color), folder.emoji) else FileGlyph(file?.mimeType)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = marked(name, SearchText.highlights(name, hit.nameRanges), mark),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (file != null) "$place · ${formatBytes(file.size)}" else place,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val snippet = hit.snippet
                if (!snippet.isNullOrBlank()) {
                    val (text, marks) = SearchText.trimLead(snippet, SearchText.highlights(snippet, hit.snippetRanges))
                    Text(
                        text = marked(text, marks, mark.copy(color = MaterialTheme.colorScheme.onSurface)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** [text] içinde [marks] aralıklarını [style] ile vurgular. */
private fun marked(text: String, marks: List<IntRange>, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(text)
    for (range in marks) {
        if (range.first >= 0 && range.last < text.length) addStyle(style, range.first, range.last + 1)
    }
}

/** Listenin üstünde, ortalanmış bilgi metni. */
@Composable
private fun TopHint(text: String) {
    CenteredHint(text, Modifier.fillMaxWidth())
}

/** Küçük, soluk not. */
@Composable
private fun NoteText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    )
}
