# Changelog

All notable changes to this project are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added
- **Typed `koGenNavigation { }` Gradle DSL**, in a new `navigation-compose-gradle-plugin`
  artifact (`io.github.eugenprog.kogen-navigation`) - replaces the string-based
  `ksp { arg(...) }` block with real enums (`NavigationAnimation`, the new
  `ViewModelInjectorKind`), autocomplete, and compile-time-checked config. The plugin also adds
  the `navigation-compose`/`navigation-compose-compiler` dependencies for you, at a matching
  version - it does *not* apply `com.google.devtools.ksp` itself (that version is tied to your
  own Kotlin version, so you still apply it yourself). The old raw `ksp { arg(...) }` block still
  works unchanged, for anyone who'd rather not add the extra plugin dependency.
- **Multi-module build support** - a `buildMode` option (`single` by default, matching every
  existing setup unchanged) alongside two new ones:
  - `module`, for a feature module meant to be combined into a larger app: instead of a
    self-contained `NavHost`, generates a `NavGraphBuilder` extension function meant to be called
    inside *someone else's* `NavHost { }`, so every module's screens end up sharing one
    graph/back stack. Also writes a small manifest describing that module's own screens.
  - `aggregator`, for the module that combines them (typically the real Android application
    module): reads every `module`'s manifest, validates that no two of them registered the same
    route (a compile error naming both, instead of a confusing runtime crash), picks an overall
    `startDestination`, and generates one combined `NavHost` calling every module's screens.
  - The hand-off between modules is a Gradle `Configuration` pair (tagged with a custom
    `Category` attribute), registered by the Gradle plugin above and gated entirely on
    `buildMode` - a module not opting into `module`/`aggregator` sees zero extra Gradle wiring at
    all, the same as before this release.
- KDoc across the entire public API and KSP compiler, including on the code the compiler itself
  generates (`navigateSafety`/`popBackSafety`/`getResultData`, and now the `module` mode's own
  generated functions too) - opening a generated file in your IDE now shows real documentation,
  not bare, uncommented code.

## [1.0.1] - 2026-08-07

### Added
- Split the library into three Maven artifacts: `navigation-compose-common` (the `@KoGenScreen`
  annotation and shared runtime types), `navigation-compose` (the runtime), and
  `navigation-compose-compiler` (the KSP processor). Consumers add two dependencies
  (`navigation-compose` + `navigation-compose-compiler` via `ksp`) instead of the old pattern of
  adding the same single artifact twice under `implementation` and `ksp` - which also used to
  leave the runtime unable to declare its own transitive dependencies, since a KSP processor
  module can't carry Android/Compose dependencies for its consumer.
- `screenSuffix` KSP option - opt-in, case-insensitive stripping of a suffix (e.g. `"Screen"`)
  from generated screen/route/action names. Only the last occurrence is removed, so a name that
  happens to contain the suffix earlier too (`ScreenshotScreen`) keeps that part intact
  (`Screenshot`, not `hot`). Unset by default - nothing is stripped unless configured.
- `deepLinks` parameter on `@KoGenScreen` - a list of full deep link URI patterns
  (`["myapp://chat/{chatId}", "https://example.com/chat/{chatId}"]`) generated straight into
  that screen's `composable(...)` call. A `{placeholder}` that doesn't match any of the screen's
  own parameters produces a KSP warning instead of silently failing at runtime.
- Full test suite covering the KSP processor: every generator's output is verified by actually
  compiling it (`kotlin-compile-testing`) against hand-written Android/Compose/Koin stand-ins,
  not just string-matching the generated source.
- CI runs the full test suite on every pull request; publishing a new version is blocked at the
  CI level if any test fails.

### Changed
- Code generators rewritten with KotlinPoet instead of hand-built strings - generated files now
  get correct imports automatically instead of relying on fully-qualified names sprinkled through
  every line.
- `isViewModel()` detection switched from string-matching a parameter's default-value expression
  (which only recognized `viewModel()`-style calls) to checking the real type hierarchy against
  `androidx.lifecycle.ViewModel`, so any way of obtaining the instance is recognized correctly.

### Fixed
- **`List<Custom>`/`Array<Custom>` parameters of a custom (Gson-deserialized) type generated
  invalid Kotlin** - the type printed as `kotlin.collections.List<Custom>` with the inner
  `Custom` left unqualified and unresolved, since the generated file never imports anything
  beyond the screen function itself.
- **A nullable custom-type parameter's Gson deserialization could crash instead of yielding
  `null`** for a genuinely absent argument.
- **`Float` parameters with a non-null default failed to compile** (`?: 0` - Kotlin doesn't
  auto-widen an `Int` literal to `Float`, unlike `Long`); now emits `?: 0f`.
- **`NavigationExtensions.kt` (and the package-name inference) could be generated twice**, under
  two different packages, because KSP invokes `process()` once per round (several per
  compilation) and the file was being (re)written on every round instead of once.
- **The KSP processor crashed with a file-already-exists-style exception on incremental
  rebuilds**, masked by a blanket `try/catch` around every generator that silently swallowed the
  exception. Removed at the root instead of papering over it: code generation now runs exactly
  once per compilation, and the `try/catch` is gone.
- Unchecked generic cast in the old `annotationParameterByName<T>()` helper - replaced with
  dedicated, safely-cast accessors per concrete annotation-parameter type actually used.

## [1.0.0] - 2025-06-07
- Initial public release.
