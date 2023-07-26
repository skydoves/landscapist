# Overview

![banner](https://user-images.githubusercontent.com/24237865/127760344-bb042fe8-23e1-4014-b208-b7b549d32086.png)

🌻 Landscapist is a highly optimized, pluggable Jetpack Compose image loading solution that fetches and displays network images with [Glide](https://github.com/bumptech/glide), [Coil](https://github.com/coil-kt/coil), and [Fresco](https://github.com/facebook/fresco). This library supports tracing image loading states, composing custom implementations, and some valuable animations, such as crossfades, blur transformation, and circular reveals. You can also configure and attach image-loading behaviors easily and fast with image plugins.

## Why Landscapist?

Landscapist is built with a lot of consideration to improve the performance of image loadings in Jetpack Compose. Most composable functions of Landscapist are **Restartable** and **Skippable**, which indicates fairly improved recomposition performance according to the Compose compiler metrics. Also, the library performance was improved with [Baseline Profiles](https://android-developers.googleblog.com/2022/01/improving-app-performance-with-baseline.html) and it supports many pluggable features, such as [ImageOptions](https://github.com/skydoves/landscapist#imageoptions), [listening image state changes](https://github.com/skydoves/landscapist#listening-image-state-changes), [custom composables](https://github.com/skydoves/landscapist#custom-composables), [preview on Android Studio](https://github.com/skydoves/landscapist#preview-on-android-studio), [ImageComponent and ImagePlugin](https://github.com/skydoves/landscapist#imagecomponent-and-imageplugin), [placeholder](https://github.com/skydoves/landscapist#placeholder), [animations (circular reveal, crossfade)](https://github.com/skydoves/landscapist#animation), [transformation (blur)](https://github.com/skydoves/landscapist#transformation), and [palette](https://github.com/skydoves/landscapist#palette).

!!! note "See the Compose compiler metrics for Landscapist"
    
    ![metrics](https://user-images.githubusercontent.com/24237865/201906004-f4490bdf-7af9-4ad6-b586-7dcc6f07d0c8.png)


## Who's using Landscapist?

Landscapist hits **+300,000** downloads every month around the globe! 🚀

![world](https://user-images.githubusercontent.com/24237865/196018576-a9c87534-81a2-4618-8519-0024b67964bf.png)

Especially, the global products below are using Landscapist.

### [Twitter for Android](https://play.google.com/store/apps/details?id=com.twitter.android)
- **[License](https://user-images.githubusercontent.com/24237865/125583736-f0ffa76f-8f87-433b-a9fd-192231dc5e63.jpg)**

[![twitter](https://user-images.githubusercontent.com/24237865/125583182-9527dd48-433e-4e17-ae52-3f2bb544a847.jpg)](https://play.google.com/store/apps/details?id=com.twitter.android)

### [Azar for Android](https://play.google.com/store/apps/details?id=com.azarlive.android)
- **[License](https://user-images.githubusercontent.com/24237865/155270807-5edcab23-2690-4c05-a068-885ee5558b25.jpeg)**

[![Azar](https://user-images.githubusercontent.com/24237865/155271118-2bbd5087-58b3-4360-a545-8fe4fc42efc8.jpg)](https://play.google.com/store/apps/details?id=com.azarlive.android)

### [Hakuna: Live Streams and Chat](https://play.google.com/store/apps/details?id=com.movefastcompany.bora)
<img src="https://user-images.githubusercontent.com/24237865/218469230-64747182-cda3-443c-b90f-b43728d63ffa.png" width="17%" />

### [Faire for Android](https://play.google.com/store/apps/details?id=com.faire.retailer&hl=en_CA&gl=US)

[![Faire](https://user-images.githubusercontent.com/24237865/158280614-2740e38d-ca47-49f8-a493-3eb98d7e6b27.png)](https://play.google.com/store/apps/details?id=com.faire.retailer&hl=en_CA&gl=US)
