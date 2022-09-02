plugins {
    `kotlin-dsl`
}

group = "io.getstream.whatsappclone.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplicationCompose") {
            id = "landscapist.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "landscapist.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("spotless") {
            id = "landscapist.spotless"
            implementationClass = "SpotlessConventionPlugin"
        }
    }
}
