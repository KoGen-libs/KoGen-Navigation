package kz.evko.navigationplugin

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import kz.evko.processor.annotation.GenerateScreens
import kz.evko.processor.annotation.ViewModelInjector
import org.koin.androidx.compose.koinViewModel


@GenerateScreens
@Composable
fun UserProfileScreen(navController: NavHostController) {

}

@GenerateScreens
@Composable
fun UserSettingsScreen() {

}

@GenerateScreens(
    startDestination = true,
    viewModelInjector = ViewModelInjector.KOIN,
    )
@Composable
fun SplashScreen(
    navController: NavHostController,
    show: Boolean?,
    text: String?,
    myFirstParamNumber: Int,
    list: Array<Boolean>,
     viewModel: SplashViewMod = koinViewModel(),
) {

}

class SplashViewMod : TotalViewModel() {

}

abstract class BaseViewModel : ViewModel() {

}

abstract class TotalViewModel : BaseViewModel() {

}

@GenerateScreens(navHostName = "MainNavHost")
@Composable
fun MainScreen(
    navController: NavHostController,
    // user: User = koinInject(),
) {

}
