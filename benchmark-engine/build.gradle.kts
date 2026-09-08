/*
 * Designed and developed by 2020-2023 skydoves (Jaewoong Eum)
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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
  kotlin("jvm")
  application
  alias(libs.plugins.jetbrains.compose)
  alias(libs.plugins.compose.compiler)
  id("landscapist.spotless")
}

kotlin {
  jvmToolchain(17)
}

// Not published. A device free, reproducible head to head between the landscapist-core engine and
// the real Coil engine, so loader claims can be checked without an emulator in the loop.
application {
  mainClass.set("com.skydoves.landscapist.benchmark.EngineBenchmarkKt")
}

dependencies {
  implementation(project(":landscapist"))
  implementation(project(":landscapist-core"))
  implementation(project(":landscapist-image"))
  implementation(project(":landscapist-placeholder"))
  implementation(libs.coil3)
  implementation("io.coil-kt.coil3:coil-compose:${libs.versions.coil3.get()}")
  implementation(compose.desktop.currentOs)
  implementation(compose.foundation)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okio)
  runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-${skikoHostTarget()}:${libs.versions.skiko.get()}")
}

/** Resolves the skiko native runtime classifier (e.g. "macos-arm64") for the running host. */
fun skikoHostTarget(): String {
  val os = System.getProperty("os.name").lowercase()
  val arch = System.getProperty("os.arch").lowercase()
  val osPart = when {
    os.contains("mac") || os.contains("darwin") -> "macos"
    os.contains("windows") -> "windows"
    else -> "linux"
  }
  val archPart = if (arch.contains("aarch64") || arch.contains("arm64")) "arm64" else "x64"
  return "$osPart-$archPart"
}

// Nothing consumes a distribution of a benchmark, and building one on every assemble both wastes
// CI time and trips over duplicate jars in the Compose dependency graph. `run` is the entry point.
tasks.named("distTar") { enabled = false }
tasks.named("distZip") { enabled = false }

// Allocation profiling for one Compose variant at a time, which is how the numbers above were
// tracked down: `./gradlew :benchmark-engine:run -Pjfr=/path/rec.jfr -Pprofile=landscapist-resize`.
// Without -Pjfr the benchmark runs normally.
tasks.named<JavaExec>("run") {
  // `-Pspread=5` re-runs the contested rows in five more JVMs and prints the spread, so a number
  // can be told apart from the machine it was measured on. Off by default: it costs five more runs.
  providers.gradleProperty("spread").orNull?.let { environment("LANDSCAPIST_SPREAD_RUNS", it) }
  val recording = providers.gradleProperty("jfr").orNull
  if (recording != null) {
    jvmArgs(
      "-XX:StartFlightRecording=settings=profile,filename=$recording,dumponexit=true",
      "-XX:StartFlightRecording:jdk.ObjectAllocationSample#throttle=6000/s",
    )
    environment("LANDSCAPIST_PROFILE", providers.gradleProperty("profile").getOrElse("landscapist"))
  }
}

tasks.withType<KotlinJvmCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
    freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
  }
}
