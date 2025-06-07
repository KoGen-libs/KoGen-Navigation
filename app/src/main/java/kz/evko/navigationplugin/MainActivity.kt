package kz.evko.navigationplugin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kz.evko.navigation.annotation.KoGenScreen
import kz.evko.navigation.helpers.BackStackData
import kz.evko.navigation.helpers.NavigationResultKey
import kz.evko.navigation.navigation.ActionToFourth
import kz.evko.navigation.navigation.ActionToSecond
import kz.evko.navigation.navigation.ActionToThird
import kz.evko.navigation.navigation.AppNavHost
import kz.evko.navigation.navigation.getResultData
import kz.evko.navigation.navigation.navigateSafety
import kz.evko.navigation.navigation.popBackSafety

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppNavHost(navController = rememberNavController())
        }
    }
}


@KoGenScreen
@Composable
fun MainScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (navController.getResultData(NavigationResultValues.ShowToast) == true) {
            Toast.makeText(context, "It's a toast from nav result", Toast.LENGTH_SHORT).show()
        }
    }

    Screen(
        color = Color.White,
        title = "Главная",
        routeClick = {
            navController.navigateSafety(
                ActionToSecond(
                    title = "Второй",
                )
            )
        },
        backClick = {}
    )
}

@KoGenScreen
@Composable
fun SecondScreen(
    navController: NavHostController,
    title: String,
) {
    BackHandler {
        navController.popBackSafety()
    }

    Screen(
        color = Color.White,
        title = title,
        routeClick = {
            navController.navigateSafety(
                ActionToThird(
                    screenNumber = 3,
                    screenColor = Color.Red,
                )
            )
        },
        backClick = {
            navController.popBackSafety(
                backStackData = BackStackData(NavigationResultValues.ShowToast, true)
            )
        }
    )
}

@KoGenScreen
@Composable
fun ThirdScreen(
    navController: NavHostController,
    screenNumber: Int,
    screenColor: Color,
) {
    BackHandler {
        navController.popBackSafety()
    }
    Screen(
        color = screenColor,
        title = screenNumber.toString(),
        routeClick = {
            navController.navigateSafety(
                ActionToFourth(
                    screenColor = Color.Blue,
                    title = "Четвертый",
                    titleColor = Color.White,
                )
            )
        },
        backClick = {
            navController.popBackSafety()
        }
    )
}

@KoGenScreen
@Composable
fun FourthScreen(
    navController: NavHostController,
    screenColor: Color,
    titleColor: Color,
    title: String? = null,
) {
    BackHandler {
        navController.popBackSafety()
    }
    Screen(
        titleColor = titleColor,
        color = screenColor,
        title = title ?: "Nullable param",
        routeClick = {
            navController.popBackSafety()
        },
        backClick = {
            navController.popBackSafety()
        }
    )
}

@Composable
fun Screen(
    color: Color,
    titleColor: Color = Color.Black,
    title: String,
    routeClick: () -> Unit,
    backClick: () -> Unit,
) {
    Scaffold(
        containerColor = color,
        topBar = {
            Text(
                title,
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .height(56.dp),
                textAlign = TextAlign.Center,
                color = titleColor,
            )
        }
    ) {
        Column(modifier = Modifier.padding(it)) {
            Button(
                onClick = routeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Перейти")
            }
            Button(
                onClick = backClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Go back")
            }
        }
    }
}

sealed class NavigationResultValues<T>(override val key: String, override val defaultValue: T) :
    NavigationResultKey<T> {
    data object ShowToast : NavigationResultValues<Boolean>("showToast", false)
}
