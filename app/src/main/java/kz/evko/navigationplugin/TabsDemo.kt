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
import kz.evko.navigation.navigation.ActionToTabHome
import kz.evko.navigation.navigation.ActionToTabProfile
import kz.evko.navigation.navigation.AppTabsHost
import kz.evko.navigation.routes.navigateSafety

// One shared bottom bar, two sibling tabs - both share graph = "mainTabs" (the grouping key), so
// they nest as siblings inside *one* navigation("mainTabs") { } block, not two separate ones.
// BuildMode.Single (this module sets no buildMode - the compiler default) - @KoGenTab alone, no
// @KoGenScreen: verifies a tab screen needs no second annotation, combined into the generated
// AppTabsHost below (tabsHostName's own default). Each screen still gets its own typed
// ActionToTabHome/ActionToTabProfile (targeting its own route, not the shared graph's route) - generated
// per-project - but navigateSafety(action: TabNavigationAction) itself is the real, hand-written
// overload from koGenNavigation, not regenerated here.
//
// Each screen keeps a rememberSaveable counter - not plain remember - specifically to exercise
// popUpTo(...) { saveState = true } / restoreState = true: plain `remember` state doesn't survive
// a tab's composition being torn down on switch, only state hooked into the back stack entry's own
// SavedStateRegistry (which is what rememberSaveable does) does.
@KoGenTab(graph = "mainTabs", startDestination = true)
@Composable
fun HomeScreen() {
    var count by rememberSaveable { mutableStateOf(0) }
    Column {
        Text("Home tab, count = $count")
        Button(onClick = { count++ }) { Text("Increment") }
    }
}

@KoGenTab(graph = "mainTabs")
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
                    selected = currentDestination?.hierarchy?.any { it.route == "home" } == true,
                    onClick = { navController.navigateSafety(ActionToTabHome) },
                    icon = {},
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == "profile" } == true,
                    onClick = { navController.navigateSafety(ActionToTabProfile) },
                    icon = {},
                    label = { Text("Profile") },
                )
            }
        },
    ) { innerPadding ->
        AppTabsHost(modifier = Modifier.fillMaxSize().padding(innerPadding), navController = navController)
    }
}
