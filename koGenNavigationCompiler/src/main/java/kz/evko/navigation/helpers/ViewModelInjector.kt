package kz.evko.navigation.helpers

import com.squareup.kotlinpoet.MemberName

/**
 * Which DI framework's ViewModel-provider function the generated NavHost should call for a
 * screen's ViewModel parameter (e.g. `viewModel: ProfileViewModel = koinViewModel()` becomes
 * `viewModel = koinViewModel<ProfileViewModel>()` in the generated composable() call).
 *
 * [injectorFunction] is a real [MemberName], not a raw import string - KotlinPoet auto-imports it
 * (and, wherever it's referenced via `%T`, its type argument too) from actual usage. There's no
 * hand-maintained import list to forget an entry in, which is exactly how this used to be broken
 * (Hilt's injector function had no import at all; the ViewModel type argument's import was
 * missing for every injector).
 */
enum class ViewModelInjector(
    val diName: String,
    val injectorFunction: MemberName?,
) {
    Koin("koin", MemberName("org.koin.androidx.compose", "koinViewModel")),
    Hilt("hilt", MemberName("androidx.lifecycle.viewmodel.compose", "viewModel")),
    None("", null),
}
