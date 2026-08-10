package kz.evko.navigation.helpers

/**
 * A value to hand back through the back stack when popping - pass it to the generated
 * `popBackSafety(backStackData = ...)`, which stores [value] under [data]'s [NavigationResultKey.key]
 * on the previous back-stack entry's `SavedStateHandle`. The screen being returned to reads it back
 * with the generated `getResultData(data)`.
 */
data class BackStackData<T>(
    val data: NavigationResultKey<T>,
    val value: T,
)

/**
 * Identifies one back-stack result slot. Implement with a `sealed class`/`object` per result kind
 * (mirroring how a screen's own route arguments are typed) rather than a raw string key, so
 * [BackStackData] and `getResultData` stay type-safe end to end.
 */
interface NavigationResultKey<T> {
    /** Value `getResultData` returns when nothing was ever stored under [key]. */
    val defaultValue: T?

    /** `SavedStateHandle` key this result is stored/looked up under. Must be unique per result kind. */
    val key: String
}