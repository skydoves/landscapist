import com.github.skydoves.landscapist.Configuration
import com.github.skydoves.landscapist.Dependencies
import com.github.skydoves.landscapist.Versions

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

android {
  compileSdk = Configuration.compileSdk
  defaultConfig {
    applicationId = "com.skydoves.benchmark.landscapist.app"
    minSdk = 23
    targetSdk = Configuration.targetSdk
    versionCode = Configuration.versionCode
    versionName = Configuration.versionName
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  composeOptions {
    kotlinCompilerExtensionVersion = Versions.COMPOSE_COMPILER
  }

  buildFeatures {
    compose = true
  }

  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_1_8.toString()
  }

  packagingOptions {
    resources {
      excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
  }

  buildTypes {
    create("benchmark") {
      isDebuggable = true
      signingConfig = getByName("debug").signingConfig
      matchingFallbacks += listOf("release")
    }
  }
}

dependencies {
  implementation(project(":landscapist"))

  implementation(Dependencies.material)
  implementation(Dependencies.composeUI)
  implementation(Dependencies.composeActivity)
  implementation(Dependencies.composeMaterial)
  implementation(Dependencies.composeRuntime)
  implementation(Dependencies.composeTooling)
}