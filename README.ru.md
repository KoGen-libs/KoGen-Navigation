[![Maven Central](https://img.shields.io/maven-central/v/io.github.eugenprog/navigation-compose)](https://central.sonatype.com/artifact/io.github.eugenprog/navigation-compose)

# KoGen Navigation: Руководство пользователя

**KoGen Navigation** — это библиотека для Jetpack Compose, которая использует кодогенерацию (KSP) для создания типобезопасной (type-safe) и удобной системы навигации, избавляя вас от рутинного кода.

[Read in English](README.md)

---

## 🆕 Переходите с 1.x?

Ничего не ломается. Любой проект на 1.x продолжает работать точно так же, как и раньше — новый Gradle-плагин и режимы мультимодульной сборки ниже полностью опциональны и по умолчанию выключены. Читайте дальше, когда будете готовы к тому или другому — обязательного перехода нет.

---

## 🚀 Установка и Настройка

Библиотека опубликована в **Maven Central**. Есть два способа её настроить — выберите один:

- **Вариант A — Gradle-плагин `koGenNavigation { }`** (рекомендуется): typed-конфиг, реальные enum'ы, автокомплит, проверка на этапе компиляции скрипта вместо тихого падения на опечатке в строке. Плюс сам добавляет зависимости на рантайм и компилятор.
- **Вариант B — сырой блок `ksp { arg(...) }`**: меньше подвижных частей, один плагин можно не подключать. Именно то, что уже было в 1.x — если это уже работает у вас, учить ничего нового не нужно.

Оба варианта настраивают один и тот же KSP-процессор — выбирайте любой, можно даже разный на разных модулях.

### Шаг 1: Подключаем плагин KSP

В любом случае сначала убедитесь, что плагин KSP подключен к вашему проекту.

**В файле `build.gradle.kts` корневого проекта:**
```kotlin
plugins {
    // ...
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
}
```
**Важно:** *Будьте внимательны с версиями. Версия KSP `2.1.0-1.0.29` означает, что вам нужна версия Kotlin `2.1.0` в вашем проекте. Всегда сверяйтесь с [официальной таблицей совместимости KSP](https://github.com/google/ksp/releases).*

**В файле `build.gradle.kts` вашего модуля:**
```kotlin
plugins {
    // ...
    id("com.google.devtools.ksp")
}
```

### Шаг 2A: Плагин `koGenNavigation { }` (рекомендуется)

```kotlin
import kz.evko.navigation.helpers.NavigationAnimation
import kz.evko.navigation.helpers.ViewModelInjectorKind

plugins {
    // ...
    id("com.google.devtools.ksp")
    id("io.github.eugenprog.kogen-navigation") version "<версия>"
}

koGenNavigation {
    packageName = "com.myawesome.project"
    defaultAnimation = NavigationAnimation.SlideLeft
    viewModelInjector = ViewModelInjectorKind.Koin
    screenSuffix = "Screen"
}

dependencies {
    // Сама библиотека Jetpack Navigation - подключаете сами, как и раньше
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Больше ничего не нужно - плагин сам добавит рантайм KoGen Navigation
    // и его KSP-процессор нужной версии.
}
```

Плагин **не** применяет `com.google.devtools.ksp` сам — версия KSP жёстко привязана к версии Kotlin в вашем проекте, поэтому её вы контролируете сами (Шаг 1 выше); плагин только требует, чтобы KSP был уже подключен, и явно об этом сообщает, если это не так.

Каждое поле опционально и работает точно так же, как его аналог из `ksp { arg(...) }` в Шаге 2B — см. таблицу там; только у `packageName` нет разумного дефолта (если не задан — определяется по пакету первой найденной `@KoGenScreen`-функции, как и всегда).

### Шаг 2B: Сырой блок `ksp { }` (альтернатива)

```kotlin
dependencies {
    // Сама библиотека Jetpack Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Рантайм KoGen Navigation
    implementation("io.github.eugenprog:navigation-compose:<версия>")
    // KSP-процессор KoGen Navigation - генерирует код на этапе сборки
    ksp("io.github.eugenprog:navigation-compose-compiler:<версия>")
}
```
**Важно:** *Версии рантайма и процессора должны совпадать.*

```kotlin
ksp {
    arg("packageName", "com.myawesome.project")
    arg("defaultAnimation", "slideLeft")
    arg("viewModelInjector", "koin")
    arg("screenSuffix", "Screen")
}
```
* `packageName` (**обязательный**, если вас не устраивает дефолт по инференсу) — нужен для того, чтобы сгенерированные классы находились в правильном пространстве имен вашего проекта.
* `defaultAnimation` (опциональный) — анимация по умолчанию для всех переходов. Возможные значения: `slideLeft`, `slideRight`, `slideUp`, `slideDown`, `fade`, `none`.
* `viewModelInjector` (опциональный) — для автоматической подстановки ViewModel. Возможные значения: `koin`, `hilt`.
* `screenSuffix` (опциональный) — суффикс, который нужно отрезать от имени экрана при формировании имени route/enum-константы/action-класса (например, `"HomeScreen"` -> `"Home"` при `screenSuffix = "Screen"`). Регистронезависимо, отрезается только *последнее* вхождение (`"ScreenshotScreen"` -> `"Screenshot"`, а не `"hot"`). По умолчанию не задан — ничего не отрезается, пока не сконфигурировано явно.

### Через version catalog (опционально)

Всё выше — с обычными строковыми литералами, для наглядности. Если в вашем проекте плагины/зависимости уже объявляются через Gradle version catalog (`gradle/libs.versions.toml`) — сейчас это рекомендуемый способ — вот эквивалент:

```toml
[versions]
ksp = "2.1.0-1.0.29" # держите синхронно с версией Kotlin - см. Шаг 1 выше
kogenNavigation = "<версия>"
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
// build.gradle.kts корневого проекта
plugins {
    alias(libs.plugins.ksp) apply false
}
```

```kotlin
// build.gradle.kts модуля - Вариант A, Gradle-плагин
plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.kogen.navigation)
}

dependencies {
    implementation(libs.androidx.navigation)
    // рантайм/компилятор указывать не нужно - плагин добавит их сам, как и со строками
}
```

```kotlin
// build.gradle.kts модуля - Вариант B, сырой ksp-блок
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

## ⚙️ Основное Использование

### 1. Создание Экрана

Все, что вам нужно сделать, — это пометить вашу Composable-функцию аннотацией `@KoGenScreen`. Параметры функции станут аргументами для навигации.

```kotlin
@KoGenScreen
@Composable
fun MyAwesomeScreen(
    // NavController будет предоставлен автоматически
    navController: NavHostController, 
    // Этот параметр будет превращен в обязательный аргумент навигации
    myArgument: String,
    // ViewModel будет обработан и исключен из списка аргументов
    viewModel: MyViewModel = koinViewModel()
) {
    // ... ваш UI ...
}
```
После сборки проекта KSP сгенерирует все необходимое для этого экрана.

### 2. Осуществление навигации

Для каждого экрана генератор создает специальный класс `ActionTo[ScreenName]` для типобезопасного перехода.

Например, для `MyAwesomeScreen` будет сгенерирован класс `ActionToMyAwesome`. Чтобы перейти на этот экран, используйте сгенерированную `extension`-функцию `navigateSafety`:

```kotlin
// Вызов навигации с другого экрана
Button(onClick = {
    navController.navigateSafety(
        ActionToMyAwesome(myArgument = "Hello from another screen!")
    )
}) { ... }
```
Вам больше не нужно помнить имена аргументов или конструировать URL вручную — если вы забудете параметр или укажете неверный тип, проект не скомпилируется.

### 3. Отображение графа навигации

Библиотека сгенерирует для вас готовую Composable-функцию `AppNavHost` (или с другим именем, указанным в `navHostName`), которую нужно просто разместить в вашем UI.

```kotlin
@Composable
fun MainScreen() {
    // ...
    val navController = rememberNavController()
    AppNavHost(navController = navController)
}
```

---

## 🧩 Мультимодульные приложения

Всё выше предполагает один модуль со всеми экранами — режим `buildMode = "single"` по умолчанию (или `buildMode = BuildMode.Single` в Gradle-плагине), без изменений по сравнению с любой версией до этой. Если ваше приложение разбито на фичевые модули, есть ещё два режима, чтобы собрать их в один общий граф навигации/back stack, а не по отдельному на каждый модуль.

### Фичевый модуль: `buildMode = "module"`

Подключается точно так же, как для одномодульного приложения, просто с указанным `buildMode`. В остальном ничего не меняется — `@KoGenScreen`, `navigateSafety`, `ActionTo<Screen>` работают идентично.

```kotlin
import kz.evko.navigation.helpers.BuildMode

koGenNavigation {
    buildMode = BuildMode.Module
    packageName = "com.myawesome.project.featurelogin"
}
```
```kotlin
// эквивалент через сырой ksp {}
ksp {
    arg("buildMode", "module")
    arg("moduleName", "feature-login") // с плагином по умолчанию берётся имя самого Gradle-проекта
    arg("packageName", "com.myawesome.project.featurelogin")
}
```

Вместо самодостаточного `NavHost` это генерит extension-функцию на `NavGraphBuilder` (например, `AppNavHostGraph(navController)`), которую нужно вызвать *внутри* чужого `NavHost { }` — см. агрегатор ниже. Плюс модуль пишет небольшой манифест со своими экранами — специально для того, чтобы этот агрегатор его прочитал.

#### `manifestVariant`

KSP запускается отдельно на каждый build variant Android-модуля (`kspDebugKotlin`, `kspReleaseKotlin`, ...), и у каждого свой отдельный вывод — то есть у модуля технически может быть разный манифест на каждый вариант, но опубликовать для агрегатора можно только один. `manifestVariant` выбирает, какой именно; по умолчанию — `"debug"`.

```kotlin
koGenNavigation {
    buildMode = BuildMode.Module
    packageName = "com.myawesome.project.featurelogin"
    manifestVariant = "debug" // какой вариант публикуется; по умолчанию "debug"
}
```

Если в модуле есть product flavors, обычной задачи `kspDebugKotlin` вообще не существует — реальные имена задач содержат флейвор (например, `kspFreeDebugKotlin`), поэтому `manifestVariant` нужно указать явно, совпадающим с одним из них (например, `manifestVariant = "freeDebug"`) — иначе сборка упадёт с понятной ошибкой, которая перечислит реально существующие в этом модуле задачи KSP.

### Модуль, который их собирает: `buildMode = "aggregator"`

Как правило — ваш реальный Android-модуль приложения.

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

Это читает манифест каждого перечисленного модуля и:
- **Проверяет, что никакие два модуля не зарегистрировали один и тот же роут** — ошибка компиляции с именами обоих модулей и экранов, вместо мутного краша в рантайме или молча неправильной навигации, когда оба окажутся в одном classpath приложения.
- **Выбирает общий `startDestination`** — экран, помеченный `@KoGenScreen(startDestination = true)`, найденный первым (по имени модуля, для детерминированного результата); если такого нет — просто первый экран вообще. В любом случае это просто дефолтное значение параметра у сгенерённого `AppNavHost` — передайте своё явно, если выбор не тот, что нужен.
- Генерит один общий `AppNavHost`, вызывающий `NavGraphBuilder`-функцию каждого модуля — свои собственные локальные экраны, если они есть у этого модуля, идут тем же путём, а не отдельным конкурирующим `NavHost` под тем же именем.

```kotlin
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    AppNavHost(navController = navController) // теперь охватывает все фичевые модули
}
```

Без Gradle-плагина передачу манифеста между модулями нужно настраивать самому — небольшой `Configuration`/`Sync`-код в `build.gradle.kts` агрегатора. Плагин регистрирует это автоматически (пара `Configuration` с меткой `Category`, резолвится лениво — модуль из списка, у которого на деле нет `buildMode = "module"`, просто пропускается, а не роняет сборку).

---

## ✨ Продвинутые Возможности

### Анимации Переходов

Вы можете легко управлять анимациями:

* **Глобально:** через параметр `defaultAnimation` в блоке `ksp` в `build.gradle.kts`.
* **Для конкретного экрана:** через параметр в аннотации `@KoGenScreen(animation = NavigationAnimation.Fade)`.

### Диплинки

Добавьте один или несколько URI-паттернов диплинков экрану — каждый пишется вручную полностью, так же как вы бы написали его для `navDeepLink { uriPattern = ... }` напрямую, потому что схема/хост/путь — это продуктовое решение, а не то, что можно вывести из сигнатуры экрана. Каждый `{placeholder}` должен совпадать с именем параметра аннотированной функции.

```kotlin
@KoGenScreen(deepLinks = ["myapp://chat/{chatId}", "https://example.com/chat/{chatId}"])
@Composable
fun ChatDetailsScreen(chatId: String) {
    // ...
}
```

### Возврат Результата с Экрана

Библиотека предоставляет удобный способ вернуть данные на предыдущий экран.

#### Шаг 1: Определяем ключ для результата
```kotlin
sealed class NavigationResultValues<T>(override val key: String, override val defaultValue: T) :
    NavigationResultKey<T> {
    data object ShowToast : NavigationResultValues<Boolean>("showToast", false)
}
```

#### Шаг 2: Возвращаем результат при уходе с экрана
Используйте сгенерированную функцию `popBackSafety` с параметром `backStackData`.

```kotlin
// На экране, который возвращает результат
Button(onClick = {
    navController.popBackSafety(
        backStackData = BackStackData(NavigationResultValues.ShowToast, true)
    )
}) { ... }
```

#### Шаг 3: Получаем результат на предыдущем экране
Используйте `getResultData` внутри `LaunchedEffect` для "прослушивания" результата.
```kotlin
// На экране, который ожидает результат
LaunchedEffect(Unit) {
    if (navController.getResultData(NavigationResultValues.ShowToast) == true) {
        Toast.makeText(context, "It's a toast from nav result", Toast.LENGTH_SHORT).show()
    }
}
```

### Инъекция ViewModel с другим DI-фреймворком

`viewModelInjector` из коробки покрывает Koin и Hilt. Для чего угодно другого — включая ваш собственный DI-контейнер — он вообще не нужен: просто задайте параметру `ViewModel` свой дефолт, ровно так же, как вы бы сделали без этой библиотеки.

```kotlin
@KoGenScreen
@Composable
fun ProfileScreen(
    navController: NavHostController,
    viewModel: ProfileViewModel = myOwnDiFramework.get(), // что угодно, что вы обычно пишете здесь
) {
    // ...
}
```

Если для этого параметра инжектор не настроен, генератор просто не передаёт его в сгенерённом вызове — Kotlin сам подставляет тот дефолт, что вы написали, точно так же, как для любого другого параметра с дефолтным значением.
