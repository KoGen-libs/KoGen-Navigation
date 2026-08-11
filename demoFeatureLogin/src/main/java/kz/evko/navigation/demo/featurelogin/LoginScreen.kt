package kz.evko.navigation.demo.featurelogin

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import kz.evko.navigation.annotation.KoGenScreen

@KoGenScreen(startDestination = true)
@Composable
fun LoginScreen(navController: NavHostController) {
    Text("Login (demoFeatureLogin)")
}
