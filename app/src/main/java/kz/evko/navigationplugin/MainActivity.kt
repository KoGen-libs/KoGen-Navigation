package kz.evko.navigationplugin

import android.os.Bundle
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kz.evko.processor.annotation.KoGenScreen
import kz.evko.processor.annotation.NavigationAnimation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppNavHost(navController = rememberNavController())
        }
    }
}


@KoGenScreen(
    animation = NavigationAnimation.SlideInRight,
)
@Composable
fun MainScreen(
    navController: NavHostController
) {
    Screen(
        color = Color.White,
        title = "Главная",
        routeClick = {
            navController.navigateSafety(
                ActionToSecond(
                    title = "Второй",
                )
            )
        }
    )
}

@KoGenScreen(
    animation = NavigationAnimation.SlideInRight,
)
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
        }
    )
}

@KoGenScreen(
    animation = NavigationAnimation.SlideInRight,
)
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
        }
    )
}

@KoGenScreen(
    animation = NavigationAnimation.SlideInRight,
)
@Composable
fun FourthScreen(
    navController: NavHostController,
    screenColor: Color,
    titleColor: Color,
    title: String,
) {
    BackHandler {
        navController.popBackSafety()
    }
    Screen(
        titleColor = titleColor,
        color = screenColor,
        title = title,
        routeClick = {
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
) {
    Scaffold(
        containerColor = color,
        topBar = {
            Text(
                title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .statusBarsPadding(),
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
        }
    }
}
