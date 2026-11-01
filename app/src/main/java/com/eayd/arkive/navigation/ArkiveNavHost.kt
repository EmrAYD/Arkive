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
import com.eayd.arkive.screens.trash.TrashScreen
import com.eayd.arkive.ui.components.AiSummaryDialogHost
import com.eayd.arkive.viewmodel.AuthViewModel
import com.eayd.arkive.viewmodel.TrashViewModel

/** Uygulamanın tüm ekranları ve aralarındaki geçişler. */
@Composable
fun ArkiveNavHost(navController: NavHostController = rememberNavController()) {
    SummaryNavigation(navController)
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
                // Kayıttan sonra hem kayıt hem giriş ekranı geri yığınından kalkar.
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
                // Firebase + Google oturumu temizlenir, bitince login'e geçilir.
                onLogout = {
                    authViewModel.logout(context) {
                        navController.navigateClearing(entry, Routes.LOGIN, popUpTo = Routes.HOME)
                    }
                },
            )
        }

        // Klasör içi: yalnızca klasörün id'si rotada taşınır (id bir UUID'dir, özel karakter içermez).
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

        // Dosya ön izlemesi: rotada yalnızca dosyanın id'si taşınır; ad ve içerik sunucudan alınır.
        // readOnly = true: çöpteki dosya ön izlenir ama değiştirilemez.
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

        // Çöpteki klasörün içi. Kökle aynı ekran ve aynı ViewModel; folderId verilince klasörün içini gösterir.
        // İçindeki klasörler aynı rotayla üst üste açılır.
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

/**
 * Özet açma gezinmesi; hangi ekranda olunursa olunsun çalışır:
 *  - Özetin PDF dosyası ([AiSummarizer.openFile]) açılınca ön izleme ekranına gidilir (AI düğmesine basınca, "Özeti aç"a
 *    dokununca ve bildirime dokununca aynı yol).
 *  - Bildirime dokunularak gelen istek ([AiSummarizer.pendingOpen]) oturum açılmış bir ekrandayken yerine getirilir; uygulama
 *    kapalıyken açılış/giriş ekranları bitene kadar bekler.
 *  - Özet penceresi ([AiSummaryDialogHost]) de burada durur: özetin dosyası yoksa özet metni hangi ekranda olunursa olunsun görünür.
 */
@Composable
private fun SummaryNavigation(navController: NavHostController) {
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    val pending by AiSummarizer.pendingOpen.collectAsState()
    val signedInScreen = route != null && route != Routes.SPLASH && route != Routes.LOGIN && route != Routes.REGISTER

    LaunchedEffect(navController) {
        AiSummarizer.openFile.collect { navController.navigate(Routes.preview(it.id)) }
    }
    LaunchedEffect(pending, signedInScreen) {
        if (pending != null && signedInScreen) {
            AiSummarizer.takePendingOpen()?.let { AiSummarizer.openSummary(it.fileId, it.fileName) }
        }
    }
    AiSummaryDialogHost()
}

// --- Geçiş sırasındaki dokunuşlar ---------------------------------------------------------------
//
// Ekran geçişi sürerken (varsayılan solma animasyonu yaklaşık 0,7 sn) çıkan ekran hala çizilir ve dokunuşları alır; geri
// dönerken çıkan ekran giren ekranın ÜSTÜNDE durur. Klasörden çıkıp hemen sol üstteki menü düğmesine basınca dokunuş
// aslında çıkmakta olan klasör ekranının geri okuna düşer ve ikinci kez "geri" çalışır: ana sayfa da yığından silinir,
// geriye gösterilecek ekran kalmaz (ekran bomboş olur). Aynı şekilde çift dokunuş aynı ekranı iki kez açar.
//
// Bu yüzden her ekranın gezinme çağrıları yalnızca o ekran şu an yığının EN ÜSTÜNDEYSE işlenir: geri dönülürken
// çıkan ekran artık üstte değildir (yok sayılır), giren ekran hemen kullanılabilir (beklemez).

/** [entry] şu an yığının en üstündeki ekran mı? */
private fun NavHostController.isTop(entry: NavBackStackEntry): Boolean = currentBackStackEntry?.id == entry.id

/** [entry] ekranından [route]'a gider; ekran artık üstte değilse (geçiş sürüyor, çift dokunuş) yok sayılır. */
private fun NavHostController.goFrom(entry: NavBackStackEntry, route: String) {
    if (isTop(entry)) navigate(route)
}

/** [entry] ekranından geri döner; ekran üstte değilse yok sayılır. Yığındaki son ekran hiçbir zaman silinmez. */
private fun NavHostController.popFrom(entry: NavBackStackEntry) {
    if (isTop(entry) && previousBackStackEntry != null) popBackStack()
}

/**
 * [entry] ekranından [route]'a gider ve geri yığınından [popUpTo] ile birlikte ona kadar olan her şeyi siler (geri tuşu
 * eski ekrana dönmesin). Ekran üstte değilse yok sayılır.
 */
private fun NavHostController.navigateClearing(entry: NavBackStackEntry, route: String, popUpTo: String) {
    if (!isTop(entry)) return
    navigate(route) { popUpTo(popUpTo) { inclusive = true } }
}
