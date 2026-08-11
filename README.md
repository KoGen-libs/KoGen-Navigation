[![Maven Central](https://img.shields.io/maven-central/v/io.github.eugenprog/navigation-compose)](https://central.sonatype.com/artifact/io.github.eugenprog/navigation-compose)

# KoGen Navigation: User Guide

**KoGen Navigation** is a library for Jetpack Compose that uses code generation (KSP) to create a type-safe and convenient navigation system, saving you from boilerplate code.

[Читать на русском](README.ru.md)

---

## 🆕 Upgrading from 1.x?

Nothing breaks. Every project on 1.x keeps working exactly as it did - the new Gradle plugin and
multi-module build modes below are both entirely opt-in, off by default. Read on when you're
ready for either; there's nothing you *have* to change.

---

## 🚀 Installation and Setup

The library is published on **Maven Central**. There are two ways to configure it - pick one:

- **Option A - the `koGenNavigation { }` Gradle plugin** (recommended): typed config, real enums,
  autocomplete, checked at Gradle-script-compile time instead of failing silently on a typo'd
  string. It also adds the runtime/compiler dependencies for you.
- **Option B - a raw `ksp { arg(...) }` block**: fewer moving parts, one less plugin to apply.
  Exactly what 1.x already had - nothing new to learn if this already works for you.

Both configure the exact same KSP processor underneath - pick whichever fits, or mix per module.

### Step 1: Apply the KSP Plugin

Either way, make sure the KSP plugin is applied to your project first.

**In your root `build.gradle.kts` file:**
```kotlin
plugins {
    // ...
    // The KSP version must match your Kotlin version
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
}
```
**Important:** *Be mindful of the versions. A KSP version of `2.1.0-1.0.29` means you need Kotlin version `2.1.0` in your project. Always refer to the [official KSP compatibility table](https://github.com/google/ksp/releases).*

**In your module's `build.gradle.kts` file:**
```kotlin
plugins {
    // ...
    id("com.google.devtools.ksp")
}
```

### Step 2A: The `koGenNavigation { }` Plugin (recommended)

```kotlin
import kz.evko.navigation.helpers.NavigationAnimation
import kz.evko.navigation.helpers.ViewModelInjectorKind

plugins {
    // ...
    id("com.google.devtools.ksp")
    id("io.github.eugenprog.kogen-navigation") version "<version>"
}

koGenNavigation {
    packageName = "com.myawesome.project"
    defaultAnimation = NavigationAnimation.SlideLeft
    viewModelInjector = ViewModelInjectorKind.Koin
    screenSuffix = "Screen"
}

dependencies {
    // The Jetpack Navigation library itself - still added by you, same as before
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Nothing else needed here - the plugin adds the KoGen Navigation runtime and its
    // KSP processor for you, at a matching version.
}
```

The plugin does *not* apply `com.google.devtools.ksp` itself - KSP's own version is tied tightly
to your project's Kotlin version, so you keep controlling that yourself (Step 1 above); the
plugin just requires it to already be applied, and errors clearly if it isn't.

Every property is optional and behaves exactly like its `ksp { arg(...) }` equivalent below -
see Step 2B's table for what each one does; only `packageName` doesn't have a sane default (it's
inferred from your first `@KoGenScreen` function's own package if left unset, same as always).

### Step 2B: A Raw `ksp { }` Block (alternative)

```kotlin
dependencies {
    // The Jetpack Navigation library itself
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // KoGen Navigation runtime
    implementation("io.github.eugenprog:navigation-compose:<version>")
    // KoGen Navigation's KSP processor - generates the code at compile time
    ksp("io.github.eugenprog:navigation-compose-compiler:<version>")
}
```
**Important:** *The versions for the runtime and the compiler must match.*

```kotlin
ksp {
    arg("packageName", "com.myawesome.project")
    arg("defaultAnimation", "slideLeft")
    arg("viewModelInjector", "koin")
    arg("screenSuffix", "Screen")
}
```
* `packageName` (**required** unless you're fine with the inferred default) – Needed so that the generated classes are placed in the correct namespace of your project.
* `defaultAnimation` (optional) – The default animation for all transitions. Possible values: `slideLeft`, `slideRight`, `slideUp`, `slideDown`, `fade`, `none`.
* `viewModelInjector` (optional) – For automatic provision of a ViewModel. Possible values: `koin`, `hilt`.
* `screenSuffix` (optional) – A suffix to strip off every screen's name when deriving its route/enum-entry/action name (e.g. `"HomeScreen"` -> `"Home"` with `screenSuffix = "Screen"`). Case-insensitive, only the *last* occurrence is stripped (`"ScreenshotScreen"` -> `"Screenshot"`, not `"hot"`). Not set by default - nothing is stripped unless configured.

### Using a Version Catalog (optional)

Everything above uses plain string literals for clarity. If your project already declares its
plugins/dependencies through a Gradle version catalog (`gradle/libs.versions.toml`) - the
currently recommended way to do it - here's the equivalent:

```toml
[versions]
ksp = "2.1.0-1.0.29" # keep in sync with your Kotlin version - see Step 1 above
kogenNavigation = "<version>"
androidxNavigation = "2.7.7"

[libraries]
androidx-navigation = { group = "androidx.navigation", name = "navigation-compose", version.ref = "androidxNavigation" }
kogen-navigation-runtime = { group = "io.github.eugenprog", name = "navigation-compose", version.ref = "kogenNavigation" }
kogen-navigation-compiler = { group = "io.github.eugenprog", name = "navigation-compose-compiler", version.ref = "kogenNavigation" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
kogen-navigation = { id = "io.github.eugenprog.kogen-navigation", version.ref = "kogenNavigation" }
```

```kotlin
// root build.gradle.kts
plugins {
    alias(libs.plugins.ksp) apply false
}
```

```kotlin
// module build.gradle.kts - Option A, the Gradle plugin
plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.kogen.navigation)
}

dependencies {
    implementation(libs.androidx.navigation)
    // no runtime/compiler entry needed here - the plugin adds them for you, same as with string literals
}
```

```kotlin
// module build.gradle.kts - Option B, the raw ksp block
plugins {
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(libs.androidx.navigation)
    implementation(libs.kogen.navigation.runtime)
    ksp(libs.kogen.navigation.compiler)
}
```

---

## ⚙️ Basic Usage

### 1. Creating a Screen

All you need to do is annotate your Composable function with `@KoGenScreen`. The function's parameters will become the navigation arguments.

```kotlin
@KoGenScreen
@Composable
fun MyAwesomeScreen(
    // NavController will be provided automatically
    navController: NavHostController, 
    // This parameter will be turned into a required navigation argument
    myArgument: String,
    // The ViewModel will be processed and excluded from the list of arguments
    viewModel: MyViewModel = koinViewModel()
) {
    // ... your UI ...
}
```
After building the project, KSP will generate everything necessary for this screen.

### 2. Performing Navigation

For each screen, the generator creates a special `ActionTo[ScreenName]` class for type-safe navigation. For example, for `MyAwesomeScreen`, an `ActionToMyAwesome` class will be generated.

To navigate to this screen, use the generated `navigateSafety` extension function:

```kotlin
// Example of a navigation call from another screen
Button(onClick = {
    navController.navigateSafety(
        ActionToMyAwesome(myArgument = "Hello from another screen!")
    )
}) {
    Text("Go to Awesome Screen")
}
```
You no longer need to remember argument names or construct URLs by hand—if you forget a parameter or provide the wrong type, the project will not compile.

### 3. Displaying the Navigation Graph

The library will generate a ready-to-use `AppNavHost` Composable function for you (or a different name if specified in `navHostName`), which you simply need to place in your UI.

```kotlin
@Composable
fun MainScreen() {
    // ...
    val navController = rememberNavController()
    AppNavHost(navController = navController)
}
```

---

## 🧩 Multi-module Apps

Everything above assumes one module with all your screens - the default `buildMode = "single"`
(or `buildMode = BuildMode.Single` with the Gradle plugin), unchanged from every version before
this one. If your app is split into feature modules, there are two more modes to combine them
into one shared navigation graph/back stack instead of a separate one per module.

### A feature module: `buildMode = "module"`

Apply the exact same way as a single-module app, just with `buildMode` set. Nothing else about
writing screens changes - `@KoGenScreen`, `navigateSafety`, `ActionTo<Screen>` all work identically.

```kotlin
import kz.evko.navigation.helpers.BuildMode

koGenNavigation {
    buildMode = BuildMode.Module
    packageName = "com.myawesome.project.featurelogin"
}
```
```kotlin
// raw ksp {} equivalent
ksp {
    arg("buildMode", "module")
    arg("moduleName", "feature-login") // defaults to the Gradle project's own name with the plugin
    arg("packageName", "com.myawesome.project.featurelogin")
}
```

Instead of a self-contained `NavHost`, this generates a `NavGraphBuilder` extension function
(e.g. `AppNavHostGraph(navController)`) meant to be called from *inside* another module's
`NavHost { }` - see the aggregator below. It also writes a small manifest describing this
module's own screens, for that aggregator to read.

#### `manifestVariant`

KSP runs once per Android build variant (`kspDebugKotlin`, `kspReleaseKotlin`, ...), each with its
own separate output - so a module could technically have a different manifest per variant, but only
one can actually be published for the aggregator to consume. `manifestVariant` picks which one;
it defaults to `"debug"`.

```kotlin
koGenNavigation {
    buildMode = BuildMode.Module
    packageName = "com.myawesome.project.featurelogin"
    manifestVariant = "debug" // which variant's manifest gets published; defaults to "debug"
}
```

If this module has product flavors, there is no plain `kspDebugKotlin` task at all - the real task
names are flavor-qualified (e.g. `kspFreeDebugKotlin`), so `manifestVariant` must be set explicitly
to match one of them (e.g. `manifestVariant = "freeDebug"`), or the build fails with a clear error
listing the real KSP task names available in this module.

### The module that combines them: `buildMode = "aggregator"`

Typically your real Android application module.

```kotlin
import kz.evko.navigation.helpers.BuildMode

koGenNavigation {
    buildMode = BuildMode.Aggregator
    packageName = "com.myawesome.project"
    featureModules = setOf(":feature-login", ":feature-cart")
}

dependencies {
    implementation(project(":feature-login"))
    implementation(project(":feature-cart"))
}
```

This reads every listed module's manifest, and:
- **Validates that no two modules registered the same route** - a compile error naming both
  modules and screens involved, instead of a confusing runtime crash or silently wrong navigation
  once both end up on the same app's classpath.
- **Picks an overall `startDestination`** - whichever screen was marked
  `@KoGenScreen(startDestination = true)` first (by module name, for a deterministic result); if
  none was, the very first screen overall. Either way it's just the generated `AppNavHost`'s
  default parameter value - pass your own explicitly if the pick isn't the one you want.
- Generates one combined `AppNavHost` calling every module's `NavGraphBuilder` function - your
  own local screens, if this module has any, go through the exact same path, not a rival
  self-contained `NavHost` under the same name.

```kotlin
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    AppNavHost(navController = navController) // now spans every feature module
}
```

Without the Gradle plugin, the manifest hand-off between modules needs its own small
`Configuration`/`Sync` wiring in the aggregator's `build.gradle.kts` - the plugin registers this
for you automatically (a `Category`-tagged `Configuration` pair, resolved leniently so a listed
project that isn't actually `buildMode = "module"` is just skipped, not a hard failure).

---

## ✨ Advanced Features

### Transition Animations

You can easily manage animations:

* **Globally:** via the `defaultAnimation` parameter in the `ksp` block in `build.gradle.kts`.
* **For a specific screen:** by overriding the global setting with a parameter in the annotation: `@KoGenScreen(animation = NavigationAnimation.Fade)`.

### Deep Links

Add one or more deep link URI patterns to a screen - each is written out in full by hand, the same way you'd write it for `navDeepLink { uriPattern = ... }` directly, since the scheme/host/path is a product decision, not something derivable from the screen's signature. Each `{placeholder}` should match a parameter name on the annotated function.

```kotlin
@KoGenScreen(deepLinks = ["myapp://chat/{chatId}", "https://example.com/chat/{chatId}"])
@Composable
fun ChatDetailsScreen(chatId: String) {
    // ...
}
```

### Returning a Result from a Screen

The library provides a convenient way to return data to the previous screen.

#### Step 1: Define a Key for the Result
```kotlin
sealed class NavigationResultValues<T>(override val key: String, override val defaultValue: T) :
    NavigationResultKey<T> {
    data object ShowToast : NavigationResultValues<Boolean>("showToast", false)
}
```

#### Step 2: Return the Result When Leaving the Screen
Use the generated `popBackSafety` function with the `backStackData` parameter.

```kotlin
// On the screen that returns a result
Button(onClick = {
    navController.popBackSafety(
        backStackData = BackStackData(NavigationResultValues.ShowToast, true)
    )
}) { ... }
```

#### Step 3: Receive the Result on the Previous Screen
Use `getResultData` inside a `LaunchedEffect` to "listen" for the result.
```kotlin
// On the screen that expects a result
LaunchedEffect(Unit) {
    if (navController.getResultData(NavigationResultValues.ShowToast) == true) {
        Toast.makeText(context, "It's a toast from nav result", Toast.LENGTH_SHORT).show()
    }
}
```

### ViewModel Injection with a Different DI Framework

`viewModelInjector` covers Koin and Hilt out of the box. For anything else - your own DI
container included - you don't need it at all: just give the `ViewModel` parameter its own
default value, the same way you would without this library.

```kotlin
@KoGenScreen
@Composable
fun ProfileScreen(
    navController: NavHostController,
    viewModel: ProfileViewModel = myOwnDiFramework.get(), // anything you'd normally write here
) {
    // ...
}
```

With no injector configured for that parameter, the generator simply omits it from the generated
call - Kotlin falls back to the default you wrote, exactly like any other parameter with one.
