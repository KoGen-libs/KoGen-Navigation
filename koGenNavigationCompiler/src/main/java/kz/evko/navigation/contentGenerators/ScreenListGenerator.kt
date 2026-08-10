package kz.evko.navigation.contentGenerators

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import kz.evko.navigation.replaceScreenWord

/**
 * Builds the `<className>` enum implementing `RouteScreenType` - one entry per screen sharing a
 * `navHostName`, each carrying the route pattern its `composable(...)` block is registered under.
 */
internal class ScreenListGenerator(
    private val packageName: String,
    private val screenSuffix: String?,
) {
    /** @param className Becomes both the generated enum's name and its file name. */
    fun generateScreenList(
        functionList: List<KSFunctionDeclaration>,
        className: String,
        logger: KSPLogger,
    ): FileSpec {
        logger.info("> for $className found ${functionList.size} screens")

        val stringType = String::class.asTypeName()
        val routeScreenType = ClassName("kz.evko.navigation.routes", "RouteScreenType")

        val enumBuilder = TypeSpec.enumBuilder(className)
            .addSuperinterface(routeScreenType)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(ParameterSpec.builder("route", stringType).build())
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("route", stringType)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("route")
                    .build(),
            )

        functionList.forEach { function ->
            enumBuilder.addEnumConstant(
                function.toString().replaceScreenWord(screenSuffix),
                TypeSpec.anonymousClassBuilder()
                    .addSuperclassConstructorParameter("%S", function.toRoutePattern(screenSuffix))
                    .build(),
            )
        }

        return FileSpec.builder(packageName, className)
            .addType(enumBuilder.build())
            .build()
    }
}

/**
 * The route pattern this screen registers under and is looked up by - lowercase screen name,
 * with `?param={param}&...` appended for each route parameter (none of it interpolated - this is
 * the pattern `composable(route = ...)` is registered with, not an actual navigable route; see
 * `RoutesListGenerator`'s `ActionTo<Screen>` for the string that fills placeholders in with real
 * values).
 */
fun KSFunctionDeclaration.toRoutePattern(screenSuffix: String?): String {
    val screenName = toString().replaceScreenWord(screenSuffix)
    val params = parameters.filter { !it.isViewModel() && !it.isNavHostController() }
    return if (params.isEmpty()) {
        screenName.lowercase()
    } else {
        params.joinToString(separator = "&", prefix = "${screenName.lowercase()}?") { param ->
            "$param={$param}"
        }
    }
}
