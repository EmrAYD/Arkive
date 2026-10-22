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
import com.eayd.arkive.screens.folders.BrowserContent
import com.eayd.arkive.screens.profile.ProfileScreen
import com.eayd.arkive.screens.trash.TrashScreen
import com.eayd.arkive.viewmodel.BrowserViewModel
import com.eayd.arkive.viewmodel.HomeViewModel
import com.eayd.arkive.viewmodel.TrashViewModel
import kotlinx.coroutines.launch

/** Profil sayfasının sağdan kayma süresi (ms). */
private const val PROFILE_SLIDE_MILLIS = 300

/**
 * Ana ekran: üst çubukta solda menü (hamburger), sağda profil düğmesi; altında seçili bölümün içeriği.
 * Menü düğmesi yan menüyü açar: bölümler (yüklenen dosyalar, favorilerim, AI sonuçları, çöp kutusu) ve
 * kalan depolama kullanımı. Profil düğmesi profil sayfasını açar.
 *
 * Her bölümün kendi ViewModel'i vardır (key); bölüm değiştirince diğerinin listesi yeniden yüklenmez.
 */
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

    // rememberSaveable: ekran döndürülse bile seçili bölüm korunur.
    var section by rememberSaveable { mutableStateOf(HomeSection.Files) }

    // Profil sayfası ana ekranın üstüne sağdan kayarak gelen bir katmandır (ayrı bir rota değil).
    var showProfile by rememberSaveable { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Menü her açıldığında kullanım bilgisi sunucudan tazelenir.
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) homeViewModel.refresh()
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
                // Scaffold padding'i kaldırıldığı için sistem çubuklarını ekran kendisi hesaba katar
                modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            ) {
                // Çöp kutusunun kendi başlığı var (geri oku, öğe sayısı, "Seç"); ana çubuk orada gösterilmez.
                if (section != HomeSection.Trash) {
                    TopAppBar(
                        title = { Text(section.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menüyü aç")
                            }
                        },
                        actions = {
                            ProfileButton(userName = state.userName, onClick = { showProfile = true })
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        // Sistem çubuğu boşluğunu yukarıdaki safeDrawingPadding zaten ayırdı; ikinci kez eklenmesin.
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    )
                    // Üst çubuğu içerikten (+ düğmesi, filtreler) ayıran çizgi.
                    HorizontalDivider()
                }

                Box(Modifier.weight(1f)) {
                    when (section) {
                        HomeSection.Trash -> TrashScreen(
                            viewModel = viewModel(key = "trash", factory = TrashViewModel.factory(folderId = null)),
                            onBack = { section = HomeSection.Files },
                            onOpenFolder = onOpenTrashFolder,
                            onOpenFile = onOpenTrashFile,
                        )

                        HomeSection.Favorites -> BrowserContent(
                            viewModel = viewModel(
                                key = "favorites",
                                factory = BrowserViewModel.factory(parentId = null, location = null, favoritesOnly = true),
                            ),
                            onOpenFolder = { onOpenFolder(it.id) },
                            onOpenFile = { onOpenFile(it.id) },
                        )

                        HomeSection.Files, HomeSection.AiResults -> BrowserContent(
                            viewModel = viewModel(
                                key = "root-${section.location}",
                                factory = BrowserViewModel.factory(parentId = null, location = section.location),
                            ),
                            onOpenFolder = { onOpenFolder(it.id) },
                            onOpenFile = { onOpenFile(it.id) },
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
            // Katmanın boş yerlerine dokunuş alttaki ana ekrana geçmesin.
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                ProfileScreen(onBack = { showProfile = false }, onLogout = onLogout)
            }
        }

        // Katman açıkken geri tuşu önce profili kapatır (alttaki seçim/menü geri işleyicilerinden önce çalışır).
        BackHandler(enabled = showProfile) { showProfile = false }
    }
}
