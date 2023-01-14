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

