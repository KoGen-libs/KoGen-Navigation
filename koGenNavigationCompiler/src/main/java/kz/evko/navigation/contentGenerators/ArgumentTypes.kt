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
        fun findType(parameter: KSValueParameter): ArgumentTypes? = entries.firstOrNull {
            it.type == parameter.type.resolve().toString().replace("?", "")
        }

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

        fun getNavArgsString(parameter: KSValueParameter): String {
            val isNullable = parameter.type.resolve().isMarkedNullable
            val type = findType(parameter) ?: StringType
            return buildString {
                appendLine("\t\t\t\tnavArgument(\"${parameter.name?.asString()}\") {")
                appendLine("\t\t\t\t\tdefaultValue = ${if (isNullable) "null" else type.defaultValue}")
                appendLine("\t\t\t\t\ttype = ${type.navArgType}")
                appendLine("\t\t\t\t\tnullable = $isNullable")
                append("\t\t\t\t},")
            }
        }
    }
}