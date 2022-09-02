import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.skydoves.landscapist.configureAndroidCompose
import com.skydoves.landscapist.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      pluginManager.apply("com.android.application")
      pluginManager.apply("org.jetbrains.kotlin.android")

      extensions.configure<BaseAppModuleExtension> {
        configureKotlinAndroid(this)
        configureAndroidCompose(this)
      }
    }
  }
}
