package kz.evko.navigation.demo.featuresettings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import kz.evko.navigation.annotation.KoGenScreen

@KoGenScreen
@Composable
fun SettingsScreen(navController: NavHostController) {
    Text("Settings (demoFeatureSettings)")
}
