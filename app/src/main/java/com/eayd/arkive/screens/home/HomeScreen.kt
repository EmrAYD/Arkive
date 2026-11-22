package com.eayd.arkive.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eayd.arkive.data.FolderLocations
import com.eayd.arkive.data.PlanStatus
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.screens.folders.BrowserContent
import com.eayd.arkive.screens.profile.ProfileScreen
import com.eayd.arkive.screens.trash.TrashScreen
import com.eayd.arkive.viewmodel.BrowserViewModel
import com.eayd.arkive.viewmodel.HomeViewModel
import com.eayd.arkive.viewmodel.TrashViewModel
import kotlinx.coroutines.launch

/** Profil sayfasının kayma süresi (ms). */
private const val PROFILE_SLIDE_MILLIS = 300

/** Ana ekran: üstte menü ve profil düğmeleri, altında seçili bölüm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onOpenFolder: (folderId: String) -> Unit,
    onOpenFile: (fileId: String) -> Unit,
    onOpenTrashFolder: (folderId: String) -> Unit,
    onOpenTrashFile: (fileId: String) -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
) {
    val state by homeViewModel.state.collectAsState()

    var section by rememberSaveable { mutableStateOf(HomeSection.Files) }

    var showProfile by rememberSaveable { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) homeViewModel.refresh()
    }
    val planName = PlanStatus.quota.collectAsState().value?.planName
    LaunchedEffect(planName) {
        val shown = state.storage?.planName
        if (planName != null && shown != null && planName != shown) homeViewModel.refresh()
    }

    Box(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ArkiveDrawerSheet(
                    selected = section,
                    storage = state.storage,
                    storageLoading = state.isLoading,
                    storageError = state.error,
                    onRetryStorage = homeViewModel::refresh,
                    onSelect = { picked ->
                        section = picked
                        scope.launch { drawerState.close() }
                    },
                )
            },
        ) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            ) {
                if (section != HomeSection.Trash) {
                    TopAppBar(
                        title = { Text(section.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = Txt.home.openMenu)
                            }
                        },
                        actions = {
                            ProfileButton(email = state.userEmail, onClick = { showProfile = true })
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    )
                    HorizontalDivider()
                }

                Box(Modifier.weight(1f)) {
                    when (section) {
                        HomeSection.Trash -> TrashScreen(
                            viewModel = viewModel(key = "trash", factory = TrashViewModel.factory(folderId = null)),
                            onBack = { section = HomeSection.Files },
                            onOpenFolder = onOpenTrashFolder,
                            onOpenFile = onOpenTrashFile,
                            onOpenMenu = { scope.launch { drawerState.open() } },
                            active = !showProfile,
                        )

                        HomeSection.Favorites -> BrowserContent(
                            viewModel = viewModel(
                                key = "favorites",
                                factory = BrowserViewModel.factory(parentId = null, location = null, favoritesOnly = true),
                            ),
                            onOpenFolder = { onOpenFolder(it.id) },
                            onOpenFile = { onOpenFile(it.id) },
                            active = !showProfile,
                        )

                        HomeSection.Files -> BrowserContent(
                            viewModel = viewModel(
                                key = "root-home",
                                factory = BrowserViewModel.factory(parentId = null, location = FolderLocations.HOME),
                            ),
                            onOpenFolder = { onOpenFolder(it.id) },
                            onOpenFile = { onOpenFile(it.id) },
                            active = !showProfile,
                        )

                        HomeSection.AiResults -> AiSummariesContent(
                            viewModel = viewModel(key = "summaries"),
                            onOpenFile = onOpenFile,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showProfile,
            enter = slideInHorizontally(animationSpec = tween(PROFILE_SLIDE_MILLIS), initialOffsetX = { it }),
            exit = slideOutHorizontally(animationSpec = tween(PROFILE_SLIDE_MILLIS), targetOffsetX = { it }),
        ) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                ProfileScreen(onBack = { showProfile = false }, onLogout = onLogout)
            }
        }

        BackHandler(enabled = showProfile) { showProfile = false }
    }
}
