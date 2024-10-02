package kz.evko.processor

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
/*import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo*/
import kz.evko.annotation.GenerateEnum
import java.util.Locale
import kotlin.reflect.KClass

/**
 * type -> private val type: Int for Enum class
 * enumConstants -> parameter from "Enum" annotation to get how many enum constants for this
 */
const val enumValueType = "type"
const val enumConstants = "enumConstants"

class EnumGenerateProcessor(private val codeGenerator: CodeGenerator) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val visitor = EnumGenerateVisitor(codeGenerator)
        val symbols = resolver.getSymbols(GenerateEnum::class)
        val validatedSymbols = symbols.filter { it.validate() }.toList()
        validatedSymbols.forEach { symbol ->
            symbol.accept(visitor, Unit)
        }

        return emptyList()
    }

    private fun Resolver.getSymbols(cls: KClass<*>) =
        this.getSymbolsWithAnnotation(cls.qualifiedName.orEmpty())
            .filterIsInstance<KSClassDeclaration>()
            .filter(KSNode::validate)
}

class EnumGenerateProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return EnumGenerateProcessor(environment.codeGenerator)
    }
}

class EnumGenerateVisitor(private val codeGenerator: CodeGenerator) : KSVisitorVoid() {
    private val enumClass = Enum::class.simpleName
    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
        val packageName = classDeclaration.packageName.asString()
        val properties = classDeclaration.getDeclaredProperties()

        properties.forEach {
            val enumAnnotation = it.annotations.find { annotation ->
                annotation.shortName.asString() == enumClass
            }
            if (enumAnnotation != null) {
                generateEnumClassForProperty(it, packageName)
            }
        }
    }

    private fun generateEnumClassForProperty(
        property: KSPropertyDeclaration,
        packageName: String
    ) {
       /* val enumAnnotation = property.annotations.find {
            it.shortName.asString() == enumClass
        }

        val listArguments = enumAnnotation?.arguments?.find { arg ->
            arg.name?.asString() == enumConstants
        }?.value as List<*>

        val propertyName = property.simpleName.asString().replaceFirstChar {
            if (it.isLowerCase())
                it.titlecase(Locale.getDefault())
            else
                it.toString()
        }

        *//**
         * public enum class GenderType(
         *   private val type: Int
         * )
         *//*
        val type = FunSpec.constructorBuilder()
            .addParameter(enumValueType, Int::class)
            .build()

        val enumClassName = ClassName(packageName, propertyName)

        val enumClass = TypeSpec.enumBuilder(propertyName)
            .primaryConstructor(type)
            .addProperty(
                PropertySpec.builder(enumValueType, Int::class)
                    .initializer(enumValueType)
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .apply {
                listArguments.forEachIndexed { index, value ->
                    addEnumConstant(
                        value.toString().uppercase(), TypeSpec.anonymousClassBuilder()
                            .addSuperclassConstructorParameter("%L", index)
                            .build()
                    ).build()
                }

                addType(
                    TypeSpec.companionObjectBuilder()
                        .addFunction(
                            FunSpec.builder("fromInt")
                                .addParameter(enumValueType, Int::class)
                                .returns(enumClassName)
                                .addCode(
                                    """
                        return values().first { it.$enumValueType == $enumValueType }
                        """.trimIndent()
                                ).build()
                        ).build()
                )
            }

        val fileSpec = FileSpec.builder(packageName, propertyName).apply {
            addType(enumClass.build())
        }.build()

        fileSpec.writeTo(codeGenerator, false)*/
    }
}

