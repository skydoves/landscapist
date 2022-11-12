import com.github.skydoves.landscapist.Configuration

plugins {
  kotlin("jvm")
}

rootProject.extra.apply {
  set("PUBLISH_GROUP_ID", Configuration.artifactGroup)
  set("PUBLISH_ARTIFACT_ID", "landscapist-bom")
  set("PUBLISH_VERSION", rootProject.extra.get("rootVersionName"))
}

dependencies {
  constraints {
    api(project(":landscapist"))
    api(project(":landscapist-animation"))
    api(project(":landscapist-palette"))
    api(project(":landscapist-placeholder"))
    api(project(":landscapist-transformation"))
    api(project(":glide"))
    api(project(":coil"))
    api(project(":fresco"))
    api(project(":fresco-websupport"))
  }
}

apply(from ="${rootDir}/scripts/publish-module.gradle")

