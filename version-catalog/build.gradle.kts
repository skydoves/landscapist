/*
 * Designed and developed by 2020-2022 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.github.skydoves.landscapist.Configuration
import com.github.skydoves.landscapist.PublishedModules
import com.vanniktech.maven.publish.VersionCatalog

plugins {
  `version-catalog`
  id(libs.plugins.nexus.plugin.get().pluginId)
}

apply(from = "${rootDir}/scripts/publish-module.gradle.kts")

val libVersion = rootProject.extra.get("libVersion").toString()

// Coordinates rather than version alignment, which is what the BOM is for. A consumer who wants
// both takes the alias from here and keeps the BOM for the alignment it does on modules that
// arrive transitively.
catalog {
  versionCatalog {
    val landscapist = version("landscapist", libVersion)

    PublishedModules.all.forEach { module ->
      library(module.alias, Configuration.artifactGroup, module.artifactId)
        .versionRef(landscapist)
    }

    // The BOM is in here too, so a consumer can take the alignment without hand writing the one
    // coordinate the catalog was meant to save them from writing.
    library("landscapist-bom", Configuration.artifactGroup, "landscapist-bom")
      .versionRef(landscapist)
  }
}

mavenPublishing {
  val artifactId = "landscapist-version-catalog"
  configure(VersionCatalog())
  coordinates(
    Configuration.artifactGroup,
    artifactId,
    libVersion
  )

  pom {
    name.set(artifactId)
  }
}
