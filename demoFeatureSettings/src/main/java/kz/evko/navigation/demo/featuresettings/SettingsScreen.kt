package kz.evko.navigation.demo.featuresettings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import kz.evko.navigation.annotation.KoGenTab

// @KoGenTab alone, no @KoGenScreen - shareTabGraph = false (see this module's build.gradle.kts) -
// this tab is meant to be fully self-contained here, never combined by demoAggregatorApp, so it's
// built locally as its own AppTabsHost instead of being reported to the manifest.
@KoGenTab(graph = "settingsTab", startDestination = true)
@Composable
fun SettingsScreen(navController: NavHostController) {
    Text("Settings (demoFeatureSettings)")
}

@KoGenTab(graph = "settingsTab")
@Composable
fun SettingsDetailsScreen(navController: NavHostController) {
    Text("Settings details (demoFeatureSettings)")
}
