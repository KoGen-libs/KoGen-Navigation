package kz.evko.navigation.contentGenerators

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter

/**
 * Fully-qualified Kotlin type name for this type, including generic type arguments (e.g. a
 * custom class used as `List<Custom>`). Used for types [ArgumentTypes.findType] doesn't
 * recognize - the ones handled via Gson - because the generated file never imports anything
 * beyond the screen function itself, so every part of the type must be spelled out or it won't
 * resolve (this was a real bug: `List<Custom>` used to print as `kotlin.collections.List<Custom>`,
 * leaving the inner `Custom` unqualified and unresolved).
 */
internal fun KSType.fullyQualifiedName(): String {
    val qualifiedName = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
    if (arguments.isEmpty()) return qualifiedName
    val typeArgs = arguments.mapNotNull { it.type?.resolve()?.fullyQualifiedName() }
    return "$qualifiedName<${typeArgs.joinToString(", ")}>"
}

/**
 * One entry per Kotlin parameter type that Jetpack Navigation's own [navArgType] system knows how
 * to carry natively (as opposed to [ArgumentTypes.getArgumentString]'s Gson fallback for anything
 * else). Each entry is every piece of source text needed to both declare and read back a route
 * argument of that type:
 *
 * @property type The parameter type as printed by [KSType.toString], nullability stripped - matched
 *   against in [findType].
 * @property getArgumentString The `NavBackStackEntry.arguments` getter call, without its `("name")`
 *   argument yet (e.g. `"it.arguments?.getBoolean"`).
 * @property defaultArgumentString Appended after that getter call for a non-nullable parameter, to
 *   turn its nullable result into the type's zero value (e.g. `" ?: false"`); a nullable parameter
 *   skips this and keeps the `?`-typed result as-is.
 * @property navArgType The matching `androidx.navigation.NavType` constant, as source text.
 * @property defaultValue The literal used as `navArgument { defaultValue = ... }` for a
 *   non-nullable parameter (a nullable one uses `null` instead - see [getNavArgsString]).
 */
enum class ArgumentTypes(
    private val type: String,
    private val getArgumentString: String,
    private val defaultArgumentString: String,
    private val navArgType: String,
    private val defaultValue: String,
) {
    BooleanType(
        "Boolean",
        "it.arguments?.getBoolean",
        " ?: false",
        "NavType.BoolType",
        "false",
    ),
    StringType(
        "String",
        "it.arguments?.getString",
        ".orEmpty()",
        "NavType.StringType",
        "\"\"",
    ),
    IntType(
        "Int",
        "it.arguments?.getInt",
        " ?: 0",
        "NavType.IntType",
        "0",
    ),
    LongType(
        "Long",
        "it.arguments?.getLong",
        " ?: 0",
        "NavType.LongType",
        "0L",
    ),
    FloatType(
        "Float",
        "it.arguments?.getFloat",
        // Must be a Float literal: unlike Long, Kotlin doesn't auto-widen a bare Int literal
        // to Float, so "?: 0" here was a hard compile error for any non-null Float parameter.
        " ?: 0f",
        "NavType.FloatType",
        "0f",
    ),
    BooleanArrayType(
        "BooleanArray",
        "it.arguments?.getBooleanArray",
        " ?: booleanArrayOf()",
        "NavType.BoolArrayType",
        "booleanArrayOf()",
    ),
    ArrayBooleanType(
        "Array<Boolean>",
        "it.arguments?.getBooleanArray",
        "?.toTypedArray() ?: arrayOf()",
        "NavType.BoolArrayType",
        "booleanArrayOf()",
    ),
    ListBooleanType(
        "List<Boolean>",
        "it.arguments?.getBooleanArray",
        "?.toList() ?: listOf()",
        "NavType.BoolArrayType",
        "booleanArrayOf()",
    ),
    ArrayStringType(
        "Array<String>",
        "it.arguments?.getStringArray",
        " ?: arrayOf()",
        "NavType.StringArrayType",
        // Explicit type argument: NavArgumentBuilder.defaultValue is typed `Any?`, which gives
        // Kotlin no context to infer a bare `arrayOf()`'s element type (unlike e.g. intArrayOf(),
        // which returns a concrete, non-generic IntArray).
        "arrayOf<String>()",
    ),
    ListStringType(
        "List<String>",
        "it.arguments?.getStringArray",
        "?.toList() ?: listOf()",
        "NavType.StringArrayType",
        "arrayOf<String>()",
    ),
    IntArrayType(
        "IntArray",
        "it.arguments?.getIntArray",
        " ?: intArrayOf()",
        "NavType.IntArrayType",
        "intArrayOf()",
    ),
    ArrayIntType(
        "Array<Int>",
        "it.arguments?.getIntArray",
        "?.toTypedArray() ?: arrayOf()",
        "NavType.IntArrayType",
        "intArrayOf()",
    ),
    ListIntType(
        "List<Int>",
        "it.arguments?.getIntArray",
        "?.toList() ?: listOf()",
        "NavType.IntArrayType",
        "intArrayOf()",
    ),
    LongArrayType(
        "LongArray",
        "it.arguments?.getLongArray",
        " ?: longArrayOf()",
        "NavType.LongArrayType",
        "longArrayOf()",
    ),
    ArrayLongType(
        "Array<Long>",
        "it.arguments?.getLongArray",
        "?.toTypedArray() ?: arrayOf()",
        "NavType.LongArrayType",
        "longArrayOf()",
    ),
    ListLongType(
        "List<Long>",
        "it.arguments?.getLongArray",
        "?.toList() ?: listOf()",
        "NavType.LongArrayType",
        "longArrayOf()",
    ),
    FloatArrayType(
        "FloatArray",
        "it.arguments?.getFloatArray",
        " ?: floatArrayOf()",
        "NavType.FloatArrayType",
        "floatArrayOf()",
    ),
    ArrayFloatType(
        "Array<Float>",
        "it.arguments?.getFloatArray",
        "?.toTypedArray() ?: arrayOf()",
        "NavType.FloatArrayType",
        "floatArrayOf()",
    ),
    ListFloatType(
        "List<Float>",
        "it.arguments?.getFloatArray",
        "?.toList() ?: listOf()",
        "NavType.FloatArrayType",
        "floatArrayOf()",
    );

    companion object {
        /** The entry matching [parameter]'s type, or `null` if it needs the Gson fallback instead. */
        fun findType(parameter: KSValueParameter): ArgumentTypes? = entries.firstOrNull {
            it.type == parameter.type.resolve().toString().replace("?", "")
        }

        /**
         * Source text that reads [parameter] back out of `it: NavBackStackEntry` inside a screen's
         * generated `composable(...)` call - a natively-typed `NavType` getter for a recognized
         * type (see [findType]), or a `Gson().fromJson(...)` call against its JSON-encoded string
         * argument for anything else.
         */
        fun getArgumentString(parameter: KSValueParameter): String {
            val name = parameter.name?.asString()
            val type = findType(parameter)
            val resolved = parameter.type.resolve()

            return type?.getArgumentString?.let {
                "$it(\"$name\")${if (resolved.isMarkedNullable) "" else type.defaultArgumentString}"
            } ?: run {
                val typeName = resolved.fullyQualifiedName()
                // TypeToken, not `Class<T>`/`::class.java`: a plain Class reference can't express
                // a parameterized type like `List<Custom>` (and would previously generate invalid
                // Kotlin for it - "List<Custom>::class.java" isn't valid syntax), and even for a
                // simple non-generic type, Gson's Class-based overload is only reliable when
                // there's no generic type argument to lose to erasure.
                val deserialize =
                    "Gson().fromJson<$typeName>(json, object : com.google.gson.reflect.TypeToken<$typeName>() {}.type)"
                if (resolved.isMarkedNullable) {
                    // Null-safe: a missing/absent argument yields null instead of trying (and
                    // failing) to parse an empty string as JSON.
                    "it.arguments?.getString(\"$name\")?.let { json -> $deserialize }"
                } else {
                    "it.arguments?.getString(\"$name\").orEmpty().let { json -> $deserialize }"
                }
            }
        }

        // No hardcoded indentation here: this is embedded via KotlinPoet's `%L`, which already
        // applies the correct indentation for whatever level it's embedded at to every line of a
        // multi-line value - hand-adding our own absolute tabs on top just doubled up on it.
        // One relative tab for the block's own inner lines is enough to tell them apart from the
        // "navArgument(...) {"/"}," lines around them.
        /** Source text of the `navArgument("name") { ... },` block declaring [parameter] on a `composable(...)` call. */
        fun getNavArgsString(parameter: KSValueParameter): String {
            val isNullable = parameter.type.resolve().isMarkedNullable
            val type = findType(parameter) ?: StringType
            return buildString {
                appendLine("navArgument(\"${parameter.name?.asString()}\") {")
                appendLine("\tdefaultValue = ${if (isNullable) "null" else type.defaultValue}")
                appendLine("\ttype = ${type.navArgType}")
                appendLine("\tnullable = $isNullable")
                append("},")
            }
        }
    }
}