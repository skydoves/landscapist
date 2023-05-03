@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.binary.compatibility) apply false
  alias(libs.plugins.baseline.profile) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.spotless)
  alias(libs.plugins.nexus.plugin)
  alias(libs.plugins.dokka)
}

apply(from = "${rootDir}/scripts/publish-root.gradle.kts")

// Set up Sonatype repository
nexusPublishing {
  this.repositories {
    sonatype {
      stagingProfileId.set(rootProject.extra.get("sonatypeStagingProfileId") as? String)
      username.set(rootProject.extra.get("ossrhUsername") as? String)
      password.set(rootProject.extra.get("ossrhPassword") as? String)
      version = rootProject.extra.get("rootVersionName")
    }
  }
}
