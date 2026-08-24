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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kz.evko.navigation.annotation.KoGenTab
import kz.evko.navigation.navigation.AppTabsHost
import kz.evko.navigation.navigation.navigateToHomeTab
import kz.evko.navigation.navigation.navigateToProfileTab

// Two single-screen tabs, BuildMode.Single (this module sets no buildMode - the compiler default)
// - @KoGenTab alone, no @KoGenScreen: verifies a tab screen needs no second annotation, and nests
// its group into one shared NavHost even with no aggregator/module split at all, combined into the
// generated AppTabsHost below (tabsHostName's own default) along with a generated
// navigateToHomeTab()/navigateToProfileTab() for the tab bar below to call.
@KoGenTab(graph = "homeTab", startDestination = true)
@Composable
fun HomeTabScreen() {
    Text("Home tab")
}

@KoGenTab(graph = "profileTab", startDestination = true)
@Composable
fun ProfileTabScreen() {
    Text("Profile tab")
}

/** A real bottom nav bar driving the generated `AppTabsHost` via the generated `navigateTo*` functions. */
@Composable
fun TabsDemo() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val currentDestination = navController.currentBackStackEntryAsState().value?.destination
            NavigationBar {
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == "homeTab" } == true,
                    onClick = { navController.navigateToHomeTab() },
                    icon = {},
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == "profileTab" } == true,
                    onClick = { navController.navigateToProfileTab() },
                    icon = {},
                    label = { Text("Profile") },
                )
            }
        },
    ) { innerPadding ->
        AppTabsHost(modifier = Modifier.fillMaxSize().padding(innerPadding), navController = navController)
    }
}
