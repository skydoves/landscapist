import com.github.skydoves.landscapist.Configuration

rootProject.extra.apply {
  set("ossrhUsername", System.getenv("OSSRH_USERNAME"))
  set("ossrhPassword", System.getenv("OSSRH_PASSWORD"))
  set("sonatypeStagingProfileId", System.getenv("SONATYPE_STAGING_PROFILE_ID"))
  set("signing.keyId", System.getenv("SIGNING_KEY_ID"))
  set("signing.password", System.getenv("SIGNING_PASSWORD"))
  set("signing.key", System.getenv("SIGNING_KEY"))
  set("snapshot", System.getenv("SNAPSHOT"))
}

val snapshot = System.getenv("SNAPSHOT") as? Boolean
if (snapshot == true) {
  rootProject.extra.set("rootVersionName", Configuration.snapshotVersionName)
} else {
  rootProject.extra.set("rootVersionName", Configuration.versionName)
}