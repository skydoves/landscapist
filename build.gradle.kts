plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.jetbrains.compose) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.binary.compatibility) apply false
  alias(libs.plugins.baseline.profile) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.stability.analyzer) apply false
  alias(libs.plugins.spotless)
  alias(libs.plugins.nexus.plugin)
  alias(libs.plugins.dokka)
}

// R8 names the .kotlin_module entry it writes after the module name recorded in Kotlin metadata,
// and the Kotlin Gradle plugin derives that name as "<root project><project path>", which puts a
// colon in it. AGP's bundle packager rejects a colon in a zip entry name, so `bundleRelease` fails
// in any consumer that minifies:
//
//   Entry name contains invalid characters:
//   root/META-INF/LandscapistDemo:landscapist-image_release.kotlin_module
//
// APK builds never showed it, because APK packaging drops META-INF/*.kotlin_module. Reported in
// #1011, and the same fix Coil took in coil-kt/coil#3587.
allprojects {
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    val moduleName = compilerOptions.moduleName.orNull
    if (moduleName != null && moduleName.contains(':')) {
      compilerOptions.moduleName.set(moduleName.replace(':', '_'))
    }
  }
}

