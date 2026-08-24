package kz.evko.navigationplugin

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kz.evko.navigation.annotation.KoGenScreen
import kz.evko.navigation.annotation.KoGenTab
import kz.evko.navigation.navigation.AppTabsHost

// Two single-screen tabs, BuildMode.Single (this module sets no buildMode - the compiler default)
// - verifies @KoGenTab nests a group into one shared NavHost even with no aggregator/module split
// at all, combined into the generated AppTabsHost below (tabsHostName's own default).
@KoGenScreen(navHostName = "homeTabHost", startDestination = true)
@KoGenTab(graph = "homeTab", startDestination = true)
@Composable
fun HomeTabScreen() {
    Text("Home tab")
}

@KoGenScreen(navHostName = "profileTabHost", startDestination = true)
@KoGenTab(graph = "profileTab", startDestination = true)
@Composable
fun ProfileTabScreen() {
    Text("Profile tab")
}

/** A real bottom nav bar driving the generated `AppTabsHost`, Google's own recommended pattern. */
@Composable
fun TabsDemo() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val currentDestination = navController.currentBackStackEntryAsState().value?.destination
            NavigationBar {
                listOf("homeTab" to "Home", "profileTab" to "Profile").forEach { (route, label) ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == route } == true,
                        onClick = {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {},
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        AppTabsHost(modifier = Modifier.fillMaxSize().padding(innerPadding), navController = navController)
    }
}
