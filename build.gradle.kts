@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.binary.compatibility) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.spotless)
  alias(libs.plugins.nexus.plugin)
  alias(libs.plugins.dokka)
}

apply(from = "${rootDir}/scripts/publish-root.gradle")

subprojects {
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = libs.versions.jvmTarget.get()
    // set compiler options
    kotlinOptions.freeCompilerArgs += listOf(
      "-Xskip-prerelease-check",
      "-Xopt-in=kotlin.RequiresOptIn",
      "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
    )
  }

  tasks.withType<org.jetbrains.dokka.gradle.DokkaMultiModuleTask> {
    outputDirectory.set(rootProject.file("docs/api"))
    failOnWarning.set(true)
  }
}
