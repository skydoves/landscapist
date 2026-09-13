# BOM

The Landscapist Bill of Materials (BOM) simplifies the management of all Landscapist library versions. By specifying only the BOM's version, you can effortlessly manage the versions of all Landscapist libraries used in your project.

 [![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://central.sonatype.com/search?q=skydoves%2520landscapist)<br>

=== "Groovy"

    ```Groovy
    dependencies {
        // Import the landscapist BOM
        implementation "com.github.skydoves:landscapist-bom:$version"

        // Import landscapist libraries
        implementation "com.github.skydoves:landscapist-glide"
        implementation "com.github.skydoves:landscapist-coil"
        implementation "com.github.skydoves:landscapist-fresco"

        implementation "com.github.skydoves:landscapist-core"
        implementation "com.github.skydoves:landscapist-image"
        implementation "com.github.skydoves:landscapist-svg"

        implementation "com.github.skydoves:landscapist-animation"
        implementation "com.github.skydoves:landscapist-placeholder"
        implementation "com.github.skydoves:landscapist-palette"
        implementation "com.github.skydoves:landscapist-transformation"
        implementation "com.github.skydoves:landscapist-zoomable"
        implementation "com.github.skydoves:landscapist-image-gallery"
    }
    ```

=== "KTS"

    ```kotlin
    dependencies {
        // Import the landscapist BOM
        implementation("com.github.skydoves:landscapist-bom:$version")

        // Import landscapist libraries
        implementation("com.github.skydoves:landscapist-glide")
        implementation("com.github.skydoves:landscapist-coil")
        implementation("com.github.skydoves:landscapist-fresco")

        implementation("com.github.skydoves:landscapist-core")
        implementation("com.github.skydoves:landscapist-image")
        implementation("com.github.skydoves:landscapist-svg")

        implementation("com.github.skydoves:landscapist-animation")
        implementation("com.github.skydoves:landscapist-placeholder")
        implementation("com.github.skydoves:landscapist-palette")
        implementation("com.github.skydoves:landscapist-transformation")
        implementation("com.github.skydoves:landscapist-zoomable")
        implementation("com.github.skydoves:landscapist-image-gallery")
    }
    ```

This ensures a streamlined and efficient development process, as you can easily keep track of library versions and ensure compatibility across your Landscapist dependencies. 
## Version catalog

The BOM settles versions. It does not save you from writing each coordinate out, and it gives you
nothing to discover the modules from. `landscapist-version-catalog` is the other half: a published
[Gradle version catalog](https://docs.gradle.org/current/userguide/version_catalogs.html) of every
Landscapist artifact, so they arrive as aliases with completion in the IDE.

Import it once in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("landscapistLibs") {
            from("com.github.skydoves:landscapist-version-catalog:$version")
        }
    }
}
```

Then declare dependencies by alias, with no version and no coordinate string:

```kotlin
dependencies {
    implementation(landscapistLibs.landscapist.glide)
    implementation(landscapistLibs.landscapist.coil3)
    implementation(landscapistLibs.landscapist.image)
    implementation(landscapistLibs.landscapist.placeholder)
}
```

Every module on this page has an alias, and so does the BOM itself, as
`landscapistLibs.landscapist.bom`.

!!! note "Which one do I want?"

    They are not alternatives. The catalog carries the coordinates and a version for the lines you
    write yourself. The BOM constrains versions across the whole resolution, including a Landscapist
    module that arrives through some other library rather than through your build file. Taking both
    is reasonable: the aliases for what you declare, the BOM for what you do not.
