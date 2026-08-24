package kz.evko.navigation.demo.featurelogin

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import kz.evko.navigation.annotation.KoGenScreen
import kz.evko.navigation.annotation.KoGenTab

// shareTabGraph is left at its default (true) - this tab spans demoFeatureCart too, so it's
// combined by demoAggregatorApp instead of being resolved locally here.
@KoGenScreen(startDestination = true)
@KoGenTab(graph = "mainTab", startDestination = true)
@Composable
fun LoginScreen(navController: NavHostController) {
    Text("Login (demoFeatureLogin)")
}
