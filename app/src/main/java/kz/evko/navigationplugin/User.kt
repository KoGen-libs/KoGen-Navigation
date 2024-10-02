package kz.evko.navigationplugin

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import kz.evko.annotation.Enum
import kz.evko.annotation.GenerateEnum
import kz.evko.annotation.GenerateScreens
import org.koin.androidx.compose.koinViewModel

@GenerateEnum
data class User(
    @Enum(enumConstants = ["ADMIN", "USER"])
    val userRole: Int = 1
)

@GenerateEnum
data class Shape(
    @Enum(enumConstants = ["Circle", "Square", "Triangle"])
    val type: Int = 0
)

class Users {
    val user = User()
    val shape = Shape()

    fun getUserRole(): Int {
        return user.userRole
    }

    fun getShapeType(): Int {
        return shape.type
    }
}

@GenerateScreens
@Composable
fun UserProfileScreen() {

}

@GenerateScreens
@Composable
fun UserSettingsScreen() {

}

@GenerateScreens(startDestination = true)
@Composable
fun SplashScreen(
    navController: NavHostController,
    users: Users,
    show: Boolean,
    text: String,
    myFirstParamNumber: Int,
    list: Array<Boolean>,
    viewModel: SplashViewMod// = koinViewModel(),
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

/*

class MyNavigationActionsFactory : NavigationRoutesFactory() {
    override fun getRoutes(): List<NavigationRouteAction> = listOf(
        NavigationRouteAction(NavigationScreens.User, NavigationScreens.UserProfile),
        NavigationRouteAction(NavigationScreens.UserProfile, NavigationScreens.User),
    )
}*/
