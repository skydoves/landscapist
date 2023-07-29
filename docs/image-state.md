# Image States

The image states indicate the current state of the image, which includes loading from the network, rendering success, or encountering failure. Based on these states, you have the flexibility to execute your own functions and implement custom behavior accordingly.

## Listening image state changes

You can listen for image state changes by providing the onImageStateChanged parameter to your image composable functions, as illustrated below:

=== "Glide"

    ```kotlin
    GlideImage(
      onImageStateChanged = {
        when (it) {
           GlideImageState.None -> ..
           GlideImageState.Loading -> ..
           is GlideImageState.Success -> ..
           is GlideImageState.Failure -> ..
        }
      },
      ..
    )
    ```

=== "Coil"

    ```kotlin
    CoilImage(
      onImageStateChanged = {
        when (it) {
           CoilImageState.None -> ..
           CoilImageState.Loading -> ..
           is CoilImageState.Success -> ..
           is CoilImageState.Failure -> ..
        }
      },
      ..
    )
    ```

=== "Fresco"

    ```kotlin
    FrescoImage(
      onImageStateChanged = {
        when (it) {
           FrescoImageState.None -> ..
           FrescoImageState.Loading -> ..
           is FrescoImageState.Success -> ..
           is FrescoImageState.Failure -> ..
        }
      },
      ..
    )
    ```

## Remember Image States

Landscapist offers valuable functions to remember image states within your Composable function and utilize them outside of the `onImageStateChanged` lambda scope. To achieve this, you can utilize the `remember_ImageState()` function as demonstrated in the sample below:

=== "Glide"

    ```kotlin
    var glideImageState by rememberGlideImageState()
    
    GlideImage(
      onImageStateChanged = {
        glideImageState = it
      },
      ..
    )
    ```

=== "Coil"

    ```kotlin
    var coilImageState by rememberCoilImageState()
    
    CoilImage(
      onImageStateChanged = {
        coilImageState = it
      },
      ..
    )
    ```

=== "Fresco"

    ```kotlin
    var frescoImageState by rememberFrescoImageState()
    
    FrescoImage(
      onImageStateChanged = {
        frescoImageState = it
      },
      ..
    )
    ```

## DataSource

You can trace the origin of the image source from the succees image state with the `DataSource` parameter. The `DataSource` encompasses the following source origins below:

- **Memory**: Represents an in-memory data source or cache (e.g. bitmap, ByteBuffer).
- **Disk**: Represents a disk-based data source (e.g. drawable resource, or File).
- **Network**: Represents a network-based data source.
- **Unknown**: Represents an unknown data source.