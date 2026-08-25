package kz.evko.navigation.demo.featuresettings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import kz.evko.navigation.annotation.KoGenTab
import kz.evko.navigation.demo.featuresettings.navigation.ActionToTabSettingsDetails
import kz.evko.navigation.routes.navigateSafety

// @KoGenTab alone, no @KoGenScreen - shareTabGraph = false (see this module's build.gradle.kts) -
// this tab is meant to be fully self-contained here, never combined by demoAggregatorApp, so it's
// built locally as its own AppTabsHost instead of being reported to the manifest.
//
// Wired with a real navigateSafety(TabNavigationAction) call (not just static text) specifically
// so this tab's own internal navigation can be clicked through on-device, not just compiled.
@KoGenTab(graph = "settingsTab", startDestination = true)
@Composable
fun SettingsScreen(navController: NavHostController) {
    Column {
        Text("Settings (demoFeatureSettings)")
        Button(onClick = { navController.navigateSafety(ActionToTabSettingsDetails) }) {
            Text("Go to details")
        }
    }
}

@KoGenTab(graph = "settingsTab")
@Composable
fun SettingsDetailsScreen(navController: NavHostController) {
    Text("Settings details (demoFeatureSettings)")
}
