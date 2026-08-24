package kz.evko.navigation.demo.aggregator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import kz.evko.navigation.demo.aggregator.navigation.AppNavHost
import kz.evko.navigation.demo.featurecart.navigation.ActionToCart
import kz.evko.navigation.demo.featuresettings.navigation.AppTabsHost as SettingsAppTabsHost
import kz.evko.navigation.helpers.navigateSafety

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DemoRoot()
        }
    }
}

/**
 * Two on-device-clickable checks the demo doesn't otherwise exercise:
 * - "Go to Cart" drives real cross-module navigation *inside* the shared "mainTab" nested graph
 *   (login lives in demoFeatureLogin, cart in demoFeatureCart) - not just that both compile into
 *   the same navigation { } block, but that navigating between them at runtime actually works.
 * - "Show settings local tab" renders demoFeatureSettings' own AppTabsHost - the shareTabGraph =
 *   false path, built entirely inside that module, never reported to this aggregator's manifest -
 *   demoAggregatorApp already depends on it, so it's a convenient place to click through it too.
 */
@Composable
private fun DemoRoot() {
    var showSettingsTab by remember { mutableStateOf(false) }
    if (showSettingsTab) {
        Column {
            Button(onClick = { showSettingsTab = false }) { Text("Back to main") }
            SettingsAppTabsHost(modifier = Modifier.weight(1f), navController = rememberNavController())
        }
    } else {
        val navController = rememberNavController()
        Column {
            Button(onClick = { showSettingsTab = true }) { Text("Show settings local tab (test)") }
            Button(onClick = { navController.navigateSafety(ActionToCart(itemCount = 5)) }) {
                Text("Go to Cart (cross-module test)")
            }
            AppNavHost(modifier = Modifier.weight(1f), navController = navController)
        }
    }
}
