package kz.evko.navigation.demo.featurecart

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import kz.evko.navigation.annotation.KoGenScreen
import kz.evko.navigation.annotation.KoGenTab

// Same tab ("mainTab") as demoFeatureLogin's LoginScreen - demoAggregatorApp combines both
// modules' contributions into one shared navigation { } block.
@KoGenScreen
@KoGenTab(graph = "mainTab")
@Composable
fun CartScreen(navController: NavHostController, itemCount: Int) {
    Text("Cart (demoFeatureCart), items: $itemCount")
}
