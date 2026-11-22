package com.eayd.arkive.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eayd.arkive.ai.AiSummarizer
import com.eayd.arkive.data.SummaryItemDto
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.ui.components.AiIcons
import com.eayd.arkive.ui.components.CenteredHint
import com.eayd.arkive.ui.components.ErrorRetry
import com.eayd.arkive.ui.components.ItemRow
import com.eayd.arkive.ui.components.ListSkeleton
import com.eayd.arkive.ui.components.NoticeEffect
import com.eayd.arkive.ui.components.NoticeHost
import com.eayd.arkive.ui.components.SummaryActionsMenu
import com.eayd.arkive.ui.components.rememberNoticeHostState
import com.eayd.arkive.viewmodel.AiSummariesViewModel

/** "AI Sonuçlarım" sayfası: özetlerin listesi. Dokununca özet açılır. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSummariesContent(
    viewModel: AiSummariesViewModel,
    onOpenFile: (fileId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val notices = rememberNoticeHostState()

    LaunchedEffect(viewModel) { viewModel.load() }
    LaunchedEffect(viewModel) { AiSummarizer.completed.collect { viewModel.reloadQuietly() } }
    LaunchedEffect(viewModel) { AiSummarizer.notices.collect(viewModel::showNotice) }
    val offlineNow by rememberUpdatedState(state.offline)
    LaunchedEffect(viewModel) { AiSummarizer.networkBack.collect { if (offlineNow) viewModel.reloadQuietly() } }
    NoticeEffect(state.notice, notices, viewModel::clearNotice)

    Column(modifier.fillMaxSize()) {
        if (state.offline) {
            Text(
                text = Txt.browser.offlineBanner,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val error = state.errorMessage
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.isLoading && state.items.isEmpty() -> ListSkeleton(Modifier.fillMaxSize())

                    error != null -> PullableCenter { ErrorRetry(error, onRetry = { viewModel.load() }) }

                    state.items.isEmpty() -> PullableCenter { CenteredHint(text = Txt.browser.emptyAiResults) }

                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            SummaryRow(
                                item = item,
                                menuEnabled = !state.offline,
                                onOpen = { AiSummarizer.showSummary(item.file.id, item.file.name, item.summary) },
                                onOpenFile = { onOpenFile(item.file.id) },
                                onDelete = { AiSummarizer.askToDelete(item.file.id, item.file.name) },
                            )
                        }
                    }
                }
            }

            NoticeHost(notices, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }
    }
}

/** Bir özetin satırı: adı "<dosya adı> özeti", altında özetin başı. */
@Composable
private fun SummaryRow(
    item: SummaryItemDto,
    menuEnabled: Boolean,
    onOpen: () -> Unit,
    onOpenFile: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ItemRow(
        title = Txt.ai.summaryOf(item.file.name),
        subtitle = item.summary.replace('\n', ' '),
        icon = { SummaryGlyph() },
        onClick = onOpen,
        trailing = {
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = menuEnabled) {
                    Icon(Icons.Filled.MoreVert, contentDescription = Txt.common.more)
                }
                SummaryActionsMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onOpenFile = onOpenFile,
                    onDelete = onDelete,
                )
            }
        },
    )
}

/** Özetin simgesi; dosya simgeleriyle aynı genişlikte. */
@Composable
private fun SummaryGlyph() {
    Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = AiIcons.Filled,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** Ortalanmış içerik; boş listede de aşağı çekerek yenilemeye izin verir. */
@Composable
private fun PullableCenter(content: @Composable () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { content() } }
    }
}
