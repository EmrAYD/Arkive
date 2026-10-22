package com.eayd.arkive.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eayd.arkive.screens.auth.LoginScreen
import com.eayd.arkive.screens.auth.RegisterScreen
import com.eayd.arkive.screens.auth.SplashScreen
import com.eayd.arkive.screens.folders.FolderScreen
import com.eayd.arkive.screens.home.HomeScreen
import com.eayd.arkive.screens.preview.PreviewScreen
import com.eayd.arkive.screens.trash.TrashScreen
import com.eayd.arkive.viewmodel.AuthViewModel
import com.eayd.arkive.viewmodel.TrashViewModel

/** Uygulamanın tüm ekranları ve aralarındaki geçişler. */
@Composable
fun ArkiveNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(
                onNavigateToLogin = { navController.navigateClearing(Routes.LOGIN, popUpTo = Routes.SPLASH) },
                onNavigateToHome = { navController.navigateClearing(Routes.HOME, popUpTo = Routes.SPLASH) },
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { navController.navigateClearing(Routes.HOME, popUpTo = Routes.LOGIN) },
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                // Kayıttan sonra hem kayıt hem giriş ekranı geri yığınından kalkar.
                onRegisterSuccess = { navController.navigateClearing(Routes.HOME, popUpTo = Routes.LOGIN) },
                onNavigateToLogin = { navController.popBackStack() },
            )
        }

        composable(Routes.HOME) {
            val authViewModel: AuthViewModel = viewModel()
            val context = LocalContext.current
            HomeScreen(
                onOpenFolder = { navController.navigate(Routes.folder(it)) },
                onOpenFile = { navController.navigate(Routes.preview(it)) },
                onOpenTrashFolder = { navController.navigate(Routes.trashFolder(it)) },
                onOpenTrashFile = { navController.navigate(Routes.preview(it, readOnly = true)) },
                // Firebase + Google oturumu temizlenir, bitince login'e geçilir.
                onLogout = {
                    authViewModel.logout(context) {
                        navController.navigateClearing(Routes.LOGIN, popUpTo = Routes.HOME)
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
                onBack = { navController.popBackStack() },
                onOpenFolder = { navController.navigate(Routes.folder(it)) },
                onOpenFile = { navController.navigate(Routes.preview(it)) },
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
                onBack = { navController.popBackStack() },
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
                onBack = { navController.popBackStack() },
                onOpenFolder = { navController.navigate(Routes.trashFolder(it)) },
                onOpenFile = { navController.navigate(Routes.preview(it, readOnly = true)) },
            )
        }
    }
}

/** [route]'a gider ve geri yığınından [popUpTo] ile birlikte ona kadar olan her şeyi siler (geri tuşu eski ekrana dönmesin). */
private fun NavHostController.navigateClearing(route: String, popUpTo: String) {
    navigate(route) { popUpTo(popUpTo) { inclusive = true } }
}
