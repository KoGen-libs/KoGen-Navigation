package kz.evko.navigationplugin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kz.evko.navigation.annotation.KoGenTab
import kz.evko.navigation.navigation.ActionToHomeTab
import kz.evko.navigation.navigation.ActionToProfileTab
import kz.evko.navigation.navigation.AppTabsHost
import kz.evko.navigation.routes.navigateSafety

// Two single-screen tabs, BuildMode.Single (this module sets no buildMode - the compiler default)
// - @KoGenTab alone, no @KoGenScreen: verifies a tab screen needs no second annotation, and nests
// its group into one shared NavHost even with no aggregator/module split at all, combined into the
// generated AppTabsHost below (tabsHostName's own default). ActionToHomeTab/ActionToProfileTab are
// generated (per-project route), but navigateSafety(action: TabNavigationAction) itself is the
// real, hand-written overload from koGenNavigation - not regenerated here.
//
// Each screen keeps a rememberSaveable counter - not plain remember - specifically to exercise
// popUpTo(...) { saveState = true } / restoreState = true: plain `remember` state doesn't survive
// a tab's composition being torn down on switch, only state hooked into the back stack entry's own
// SavedStateRegistry (which is what rememberSaveable does) does.
@KoGenTab(graph = "homeTab", startDestination = true)
@Composable
fun HomeScreen() {
    var count by rememberSaveable { mutableStateOf(0) }
    Column {
        Text("Home tab, count = $count")
        Button(onClick = { count++ }) { Text("Increment") }
    }
}

@KoGenTab(graph = "profileTab", startDestination = true)
@Composable
fun ProfileScreen() {
    var count by rememberSaveable { mutableStateOf(0) }
    Column {
        Text("Profile tab, count = $count")
        Button(onClick = { count++ }) { Text("Increment") }
    }
}

/** A real bottom nav bar driving the generated `AppTabsHost` via the library's own `navigateSafety` overload. */
@Composable
fun TabsDemo() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val currentDestination = navController.currentBackStackEntryAsState().value?.destination
            NavigationBar {
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == "homeTab" } == true,
                    onClick = { navController.navigateSafety(ActionToHomeTab) },
                    icon = {},
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == "profileTab" } == true,
                    onClick = { navController.navigateSafety(ActionToProfileTab) },
                    icon = {},
                    label = { Text("Profile") },
                )
            }
        },
    ) { innerPadding ->
        AppTabsHost(modifier = Modifier.fillMaxSize().padding(innerPadding), navController = navController)
    }
}
