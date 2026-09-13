/*
 * Copyright (C) 2020 skydoves
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

package com.github.skydoves.landscapist

/**
 * Every module that goes to Maven Central, other than the BOM and the version catalog themselves.
 *
 * Two things publish this list: `bom/build.gradle.kts` constrains each of them to the release
 * version, and `version-catalog/build.gradle.kts` turns each into a catalog alias. Adding a module
 * in one place and forgetting the other is how `landscapist-image-gallery` ended up in the BOM but
 * missing from the documented dependency list for several releases, so the list lives here and
 * both read it.
 *
 * @property path The Gradle project path.
 * @property artifactId The artifact id it publishes under, which is not always the project name.
 * @property alias The version catalog alias, which reads as `landscapistLibs.<alias with dots>`.
 */
data class PublishedModule(
  val path: String,
  val artifactId: String,
  val alias: String,
)

object PublishedModules {
  val all: List<PublishedModule> = listOf(
    PublishedModule(":landscapist", "landscapist", "landscapist"),
    PublishedModule(":landscapist-core", "landscapist-core", "landscapist-core"),
    PublishedModule(":landscapist-image", "landscapist-image", "landscapist-image"),
    PublishedModule(":landscapist-svg", "landscapist-svg", "landscapist-svg"),
    PublishedModule(
      ":landscapist-image-gallery",
      "landscapist-image-gallery",
      "landscapist-image-gallery",
    ),
    PublishedModule(":landscapist-animation", "landscapist-animation", "landscapist-animation"),
    PublishedModule(
      ":landscapist-placeholder",
      "landscapist-placeholder",
      "landscapist-placeholder",
    ),
    PublishedModule(":landscapist-palette", "landscapist-palette", "landscapist-palette"),
    PublishedModule(
      ":landscapist-transformation",
      "landscapist-transformation",
      "landscapist-transformation",
    ),
    PublishedModule(":landscapist-zoomable", "landscapist-zoomable", "landscapist-zoomable"),
    PublishedModule(":glide", "landscapist-glide", "landscapist-glide"),
    PublishedModule(":coil", "landscapist-coil", "landscapist-coil"),
    PublishedModule(":coil3", "landscapist-coil3", "landscapist-coil3"),
    PublishedModule(":fresco", "landscapist-fresco", "landscapist-fresco"),
    PublishedModule(
      ":fresco-websupport",
      "landscapist-fresco-websupport",
      "landscapist-fresco-websupport",
    ),
  )
}
