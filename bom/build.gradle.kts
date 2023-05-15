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

//import com.github.skydoves.landscapist.Configuration

plugins {
  `java-platform`
}

//rootProject.extra.apply {
//  set("PUBLISH_GROUP_ID", Configuration.artifactGroup)
//  set("PUBLISH_ARTIFACT_ID", "landscapist-bom")
//  set("PUBLISH_VERSION", rootProject.extra.get("rootVersionName"))
//}

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

//apply(from ="${rootDir}/scripts/publish-module.gradle")

