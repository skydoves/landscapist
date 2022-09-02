import com.android.build.gradle.LibraryExtension
import com.skydoves.landscapist.configureAndroidCompose
import com.skydoves.landscapist.configureKotlinAndroid
import com.skydoves.landscapist.kotlinOptions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      pluginManager.apply("com.android.library")
      pluginManager.apply("org.jetbrains.kotlin.android")
      pluginManager.apply("binary-compatibility-validator")
      pluginManager.apply("org.jetbrains.dokka")

      extensions.configure<LibraryExtension> {
        configureKotlinAndroid(this)
        configureAndroidCompose(this)

        kotlinOptions {
          freeCompilerArgs = freeCompilerArgs + listOf("-Xexplicit-api=strict")
        }
      }
    }
  }
}
