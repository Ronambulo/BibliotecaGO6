package com.bibliotecago.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.net.Uri
import com.bibliotecago.app.screens.BookDetailScreen
import com.bibliotecago.app.screens.DownloadsScreen
import com.bibliotecago.app.screens.ExploreScreen
import com.bibliotecago.app.screens.HomeScreen
import com.bibliotecago.app.screens.LibraryScreen
import com.bibliotecago.app.screens.SettingsScreen
import com.bibliotecago.core.eink.EinkBottomBar
import com.bibliotecago.core.eink.EinkBottomBarItem
import com.bibliotecago.core.eink.EinkIcons
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.model.BookId

/** Destinos de la barra inferior (§5.3). */
private enum class TopDestination(val route: String, val item: EinkBottomBarItem) {
    Home("inicio", EinkBottomBarItem("Inicio", EinkIcons.Home)),
    Library("biblioteca", EinkBottomBarItem("Biblioteca", EinkIcons.Library)),
    Downloads("descargas", EinkBottomBarItem("Descargas", EinkIcons.Download)),
    Settings("ajustes", EinkBottomBarItem("Ajustes", EinkIcons.Settings)),
}

@Composable
fun BibliotecaApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selected = TopDestination.entries.indexOfFirst { it.route == currentRoute }

    Column(
        Modifier
            .fillMaxSize()
            .background(EinkTheme.colors.paper)
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // Cambios de pantalla instantáneos: ninguna transición en ninguna dirección.
            NavHost(
                navController = navController,
                startDestination = TopDestination.Home.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
                sizeTransform = null,
            ) {
                composable(TopDestination.Home.route) {
                    HomeScreen(onOpenLibrary = { navController.navigateTop(TopDestination.Library) })
                }
                composable(TopDestination.Library.route) {
                    LibraryScreen(
                        onOpenSettings = { navController.navigateTop(TopDestination.Settings) },
                        onOpenBook = navController::navigateToBook,
                        onBrowse = { navController.navigateToFeed(it.href, it.title) },
                    )
                }
                composable(TopDestination.Downloads.route) {
                    DownloadsScreen(
                        onOpenLibrary = { navController.navigateTop(TopDestination.Library) },
                        onOpenBook = navController::navigateToBook,
                    )
                }
                // Navigation ya descodifica los argumentos.
                composable(BOOK_ROUTE) { entry ->
                    val id = BookId(entry.arguments?.getString("id").orEmpty())
                    BookDetailScreen(
                        id,
                        onBack = { navController.popBackStack() },
                        onOpenFeed = navController::navigateToFeed,
                    )
                }
                composable(
                    FEED_ROUTE,
                    arguments = listOf(
                        navArgument("href") { type = NavType.StringType; defaultValue = "" },
                        navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    ExploreScreen(
                        href = entry.arguments?.getString("href").orEmpty(),
                        title = entry.arguments?.getString("title").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onOpenFeed = navController::navigateToFeed,
                        onOpenBook = navController::navigateToBook,
                    )
                }
                composable(TopDestination.Settings.route) { SettingsScreen() }
            }
        }

        // La barra se oculta en el lector (Fase 3): solo se muestra en los destinos principales.
        if (selected >= 0) {
            EinkBottomBar(
                items = TopDestination.entries.map { it.item },
                selectedIndex = selected,
                onSelect = { navController.navigateTop(TopDestination.entries[it]) },
            )
        }
    }
}

private const val BOOK_ROUTE = "libro/{id}"
private const val FEED_ROUTE = "explorar?href={href}&title={title}"

/** Un feed OPDS por su ruta en el servidor: índices, autores, series, búsquedas… */
private fun NavController.navigateToFeed(href: String, title: String) {
    navigate("explorar?href=${Uri.encode(href)}&title=${Uri.encode(title)}")
}

/** El id OPDS lleva «:» y «/» (urn:uuid:…), así que va codificado en la ruta. */
private fun NavController.navigateToBook(id: BookId) {
    navigate("libro/${Uri.encode(id.value)}")
}

private fun NavController.navigateTop(destination: TopDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
