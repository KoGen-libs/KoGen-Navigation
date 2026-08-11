package kz.evko.navigation.demo.aggregator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import kz.evko.navigation.demo.aggregator.navigation.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppNavHost(navController = rememberNavController())
        }
    }
}
