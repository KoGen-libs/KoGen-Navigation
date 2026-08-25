package kz.evko.navigation.demo.featurelogin

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import kz.evko.navigation.annotation.KoGenTab

// @KoGenTab alone, no @KoGenScreen - shareTabGraph is left at its default (true), so this tab
// (spanning demoFeatureCart too) is combined by demoAggregatorApp instead of being resolved
// locally here.
@KoGenTab(graph = "mainTab", startDestination = true)
@Composable
fun LoginScreen(navController: NavHostController) {
    Text("Login (demoFeatureLogin)")
}
