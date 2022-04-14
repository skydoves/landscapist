package com.github.skydoves.landscapist

object Versions {
    internal const val ANDROID_GRADLE_PLUGIN = "7.1.2"
    internal const val ANDROID_GRADLE_SPOTLESS = "6.3.0"
    internal const val GRADLE_NEXUS_PUBLISH_PLUGIN = "1.1.0"
    internal const val KOTLIN = "1.6.10"
    internal const val KOTLIN_GRADLE_DOKKA = "1.6.10"
    internal const val KOTLIN_BINARY_VALIDATOR = "0.8.0"

    internal const val CORE_KTX = "1.7.0"
    internal const val COMPOSE = "1.1.1"
    internal const val COROUTINES = "1.6.0"

    internal const val FRESCO = "2.6.0"
    internal const val GLIDE = "4.13.1"
    internal const val COIL = "1.4.0"

    internal const val PALETTE = "1.0.0"

    internal const val RULES = "1.4.0"
    internal const val ANDROIDX_TEST = "1.4.0"
    internal const val ANDROIDX_JUNIT = "1.1.3"

    internal const val HILT = "2.40.5"
    internal const val MATERIAL = "1.5.0"
    internal const val COMPOSE_ACTIVITY = "1.3.0-alpha08"
    internal const val COMPOSE_CONSTRAINT = "1.0.0"
}

object Dependencies {
    const val androidGradlePlugin =
        "com.android.tools.build:gradle:${Versions.ANDROID_GRADLE_PLUGIN}"
    const val gradleNexusPublishPlugin =
        "io.github.gradle-nexus:publish-plugin:${Versions.GRADLE_NEXUS_PUBLISH_PLUGIN}"
    const val kotlinGradlePlugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.KOTLIN}"
    const val spotlessGradlePlugin =
        "com.diffplug.spotless:spotless-plugin-gradle:${Versions.ANDROID_GRADLE_SPOTLESS}"
    const val dokka = "org.jetbrains.dokka:dokka-gradle-plugin:${Versions.KOTLIN_GRADLE_DOKKA}"
    const val hiltPlugin = "com.google.dagger:hilt-android-gradle-plugin:${Versions.HILT}"
    const val kotlinBinaryValidator =
        "org.jetbrains.kotlinx:binary-compatibility-validator:${Versions.KOTLIN_BINARY_VALIDATOR}"

    const val coreKtx = "androidx.core:core-ktx:${Versions.CORE_KTX}"
    const val material = "com.google.android.material:material:${Versions.MATERIAL}"
    const val composeUI = "androidx.compose.ui:ui:${Versions.COMPOSE}"
    const val composeRuntime = "androidx.compose.runtime:runtime:${Versions.COMPOSE}"
    const val composeFoundation = "androidx.compose.foundation:foundation:${Versions.COMPOSE}"
    const val composeFoundationLayout =
        "androidx.compose.foundation:foundation-layout:${Versions.COMPOSE}"
    const val composeActivity = "androidx.activity:activity-compose:${Versions.COMPOSE_ACTIVITY}"
    const val composeMaterial = "androidx.compose.material:material:${Versions.COMPOSE}"
    const val composeMaterialIcon =
        "androidx.compose.material:material-icons-extended:${Versions.COMPOSE}"
    const val composeAnimation = "androidx.compose.animation:animation:${Versions.COMPOSE}"
    const val composeTooling = "androidx.compose.ui:ui-tooling:${Versions.COMPOSE}"
    const val composeConstraintLayout =
        "androidx.constraintlayout:constraintlayout-compose:${Versions.COMPOSE_CONSTRAINT}"

    const val coroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES}"
    const val palette = "androidx.palette:palette-ktx:${Versions.PALETTE}"

    const val coil = "io.coil-kt:coil:${Versions.COIL}"
    const val coilGif = "io.coil-kt:coil-gif:${Versions.COIL}"

    const val glide = "com.github.bumptech.glide:glide:${Versions.GLIDE}"

    const val fresco = "com.facebook.fresco:fresco:${Versions.FRESCO}"
    const val frescoOkHttp3 = "com.facebook.fresco:imagepipeline-okhttp3:${Versions.FRESCO}"
    const val frescoWebp = "com.facebook.fresco:animated-webp:${Versions.FRESCO}"
    const val frescoGif = "com.facebook.fresco:animated-gif:${Versions.FRESCO}"
    const val frescoDrawable = "com.facebook.fresco:animated-drawable:${Versions.FRESCO}"
    const val frescoWebSupport = "com.facebook.fresco:webpsupport:${Versions.FRESCO}"

    const val testRules = "androidx.test:rules:${Versions.RULES}"
    const val androidTestRunner = "androidx.test:runner:${Versions.ANDROIDX_TEST}"
    const val androidTestJunit = "androidx.test.ext:junit-ktx:${Versions.ANDROIDX_JUNIT}"
    const val composeJunitTest = "androidx.compose.ui:ui-test-junit4:${Versions.COMPOSE}"

    const val hiltAndroid = "com.google.dagger:hilt-android:${Versions.HILT}"
    const val hiltCompiler = "com.google.dagger:hilt-compiler:${Versions.HILT}"
}
