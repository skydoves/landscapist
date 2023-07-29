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

        implementation "com.github.skydoves:landscapist-animation"
        implementation "com.github.skydoves:landscapist-placeholder"
        implementation "com.github.skydoves:landscapist-palette"
        implementation "com.github.skydoves:landscapist-transformation"
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

        implementation("com.github.skydoves:landscapist-animation")
        implementation("com.github.skydoves:landscapist-placeholder")
        implementation("com.github.skydoves:landscapist-palette")
        implementation("com.github.skydoves:landscapist-transformation")
    }
    ```

This ensures a streamlined and efficient development process, as you can easily keep track of library versions and ensure compatibility across your Landscapist dependencies. 