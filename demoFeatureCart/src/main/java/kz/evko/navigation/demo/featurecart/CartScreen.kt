package kz.evko.navigation.demo.featurecart

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import kz.evko.navigation.annotation.KoGenScreen

@KoGenScreen
@Composable
fun CartScreen(navController: NavHostController, itemCount: Int) {
    Text("Cart (demoFeatureCart), items: $itemCount")
}
