package com.eayd.arkive.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eayd.arkive.ai.AiSummarizer
import com.eayd.arkive.screens.auth.LoginScreen
import com.eayd.arkive.screens.auth.RegisterScreen
import com.eayd.arkive.screens.auth.SplashScreen
import com.eayd.arkive.screens.folders.FolderScreen
import com.eayd.arkive.screens.home.HomeScreen
import com.eayd.arkive.screens.preview.PreviewScreen
import com.eayd.arkive.screens.search.SearchScreen
import com.eayd.arkive.screens.trash.TrashScreen
import com.eayd.arkive.ui.components.AiSummaryDialogHost
import com.eayd.arkive.ui.components.PlusOfferHost
import com.eayd.arkive.viewmodel.AuthViewModel
import com.eayd.arkive.viewmodel.TrashViewModel

/** Uygulamanın ekranları ve aralarındaki geçişler. */
@Composable
fun ArkiveNavHost(navController: NavHostController = rememberNavController()) {
    SummaryNavigation(navController)
    PlusOfferHost()
    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) { entry ->
            SplashScreen(
                onNavigateToLogin = { navController.navigateClearing(entry, Routes.LOGIN, popUpTo = Routes.SPLASH) },
                onNavigateToHome = { navController.navigateClearing(entry, Routes.HOME, popUpTo = Routes.SPLASH) },
            )
        }

        composable(Routes.LOGIN) { entry ->
            LoginScreen(
                onLoginSuccess = { navController.navigateClearing(entry, Routes.HOME, popUpTo = Routes.LOGIN) },
                onNavigateToRegister = { navController.goFrom(entry, Routes.REGISTER) },
            )
        }

        composable(Routes.REGISTER) { entry ->
            RegisterScreen(
                onRegisterSuccess = { navController.navigateClearing(entry, Routes.HOME, popUpTo = Routes.LOGIN) },
                onNavigateToLogin = { navController.popFrom(entry) },
            )
        }

        composable(Routes.HOME) { entry ->
            val authViewModel: AuthViewModel = viewModel()
            val context = LocalContext.current
            HomeScreen(
                onOpenFolder = { navController.goFrom(entry, Routes.folder(it)) },
                onOpenFile = { navController.goFrom(entry, Routes.preview(it)) },
                onOpenTrashFolder = { navController.goFrom(entry, Routes.trashFolder(it)) },
                onOpenTrashFile = { navController.goFrom(entry, Routes.preview(it, readOnly = true)) },
                onOpenSearch = { navController.goFrom(entry, Routes.SEARCH) },
                onLogout = {
                    authViewModel.logout(context) {
                        navController.navigateClearing(entry, Routes.LOGIN, popUpTo = Routes.HOME)
                    }
                },
            )
        }

        composable(Routes.SEARCH) { entry ->
            SearchScreen(
                onBack = { navController.popFrom(entry) },
                onOpenFolder = { navController.goFrom(entry, Routes.folder(it)) },
                onOpenFile = { navController.goFrom(entry, Routes.preview(it)) },
            )
        }

        composable(
            route = Routes.FOLDER,
            arguments = listOf(navArgument(Routes.ARG_FOLDER_ID) { type = NavType.StringType }),
        ) { entry ->
            FolderScreen(
                folderId = entry.arguments?.getString(Routes.ARG_FOLDER_ID).orEmpty(),
                onBack = { navController.popFrom(entry) },
                onOpenFolder = { navController.goFrom(entry, Routes.folder(it)) },
                onOpenFile = { navController.goFrom(entry, Routes.preview(it)) },
            )
        }

        composable(
            route = Routes.PREVIEW,
            arguments = listOf(
                navArgument(Routes.ARG_FILE_ID) { type = NavType.StringType },
                navArgument(Routes.ARG_READ_ONLY) { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            PreviewScreen(
                fileId = entry.arguments?.getString(Routes.ARG_FILE_ID).orEmpty(),
                readOnly = entry.arguments?.getBoolean(Routes.ARG_READ_ONLY) ?: false,
                onBack = { navController.popFrom(entry) },
            )
        }

        composable(
            route = Routes.TRASH_FOLDER,
            arguments = listOf(navArgument(Routes.ARG_FOLDER_ID) { type = NavType.StringType }),
        ) { entry ->
            val folderId = entry.arguments?.getString(Routes.ARG_FOLDER_ID).orEmpty()
            TrashScreen(
                viewModel = viewModel(
                    key = "trash-folder-$folderId",
                    factory = TrashViewModel.factory(folderId),
                ),
                onBack = { navController.popFrom(entry) },
                onOpenFolder = { navController.goFrom(entry, Routes.trashFolder(it)) },
                onOpenFile = { navController.goFrom(entry, Routes.preview(it, readOnly = true)) },
            )
        }
    }
}

/** Her ekranda çalışan özet pencereleri ve bildirimden gelen "özeti aç" isteği. */
@Composable
private fun SummaryNavigation(navController: NavHostController) {
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    val pending by AiSummarizer.pendingOpen.collectAsState()
    val signedInScreen = route != null && route != Routes.SPLASH && route != Routes.LOGIN && route != Routes.REGISTER

    LaunchedEffect(pending, signedInScreen) {
        if (pending != null && signedInScreen) {
            AiSummarizer.takePendingOpen()?.let { AiSummarizer.openSummary(it.fileId, it.fileName) }
        }
    }
    AiSummaryDialogHost()
}

/** [entry] yığının en üstündeki ekran mı. */
private fun NavHostController.isTop(entry: NavBackStackEntry): Boolean = currentBackStackEntry?.id == entry.id

/** [entry] ekranından [route]'a gider; ekran artık üstte değilse (çift dokunuş) bir şey yapmaz. */
private fun NavHostController.goFrom(entry: NavBackStackEntry, route: String) {
    if (isTop(entry)) navigate(route)
}

/** [entry] ekranından geri döner; yığındaki son ekran silinmez. */
private fun NavHostController.popFrom(entry: NavBackStackEntry) {
    if (isTop(entry) && previousBackStackEntry != null) popBackStack()
}

/** [route]'a gider ve [popUpTo]'ya kadar olan ekranları yığından siler. */
private fun NavHostController.navigateClearing(entry: NavBackStackEntry, route: String, popUpTo: String) {
    if (!isTop(entry)) return
    navigate(route) { popUpTo(popUpTo) { inclusive = true } }
}
