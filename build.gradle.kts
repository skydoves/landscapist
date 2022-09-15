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
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions.freeCompilerArgs += listOf(
      "-P",
      "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" +
        project.buildDir.absolutePath + "/compose_metrics"
    )
    kotlinOptions.freeCompilerArgs += listOf(
      "-P",
      "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=" +
        project.buildDir.absolutePath + "/compose_metrics"
    )
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
  }
}
