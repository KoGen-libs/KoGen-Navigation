package kz.evko.processor.contentGenerators

import com.google.devtools.ksp.symbol.KSValueParameter

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
        " ?: 0",
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
        "arrayOf()",
    ),
    ListStringType(
        "List<String>",
        "it.arguments?.getStringArray",
        "?.toList() ?: listOf()",
        "NavType.StringArrayType",
        "arrayOf()",
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
        private fun findType(parameter: KSValueParameter): ArgumentTypes? = entries.firstOrNull {
            it.type == parameter.type.resolve().toString().replace("?", "")
        }

        fun getArgumentString(parameter: KSValueParameter): String {
            val name = parameter.name?.asString()
            val type = findType(parameter)

            return type?.getArgumentString?.let {
                "$it(\"$name\")${if (parameter.type.resolve().isMarkedNullable) "" else type.defaultArgumentString}"
            }
                ?: "Gson().fromJson(it.arguments?.getString(\"$name\").orEmpty(), ${parameter.type}::class.java)"
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