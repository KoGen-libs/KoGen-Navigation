package kz.evko.navigation.helpers

data class BackStackData<T>(
    val data: NavigationResultKey<T>,
    val value: T,
)

interface NavigationResultKey<T> {
    val defaultValue: T?
    val key: String
}