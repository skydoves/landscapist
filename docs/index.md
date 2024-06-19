# Overview

![banner](https://user-images.githubusercontent.com/24237865/127760344-bb042fe8-23e1-4014-b208-b7b549d32086.png)

🌻 Landscapist is a highly optimized, pluggable Jetpack Compose image loading solution, offering seamless network image fetching and display capabilities using [Glide](https://github.com/bumptech/glide), [Coil](https://github.com/coil-kt/coil), and [Fresco](https://github.com/facebook/fresco). 

This library supports tracing image loading states, enabling you to compose custom implementations with ease. It also provides a range of valuable animations, including crossfades, blur transformations, and circular reveals, enhancing the visual appeal of your images. 

Additionally, Landscapist offers the flexibility to configure and attach image-loading behaviors effortlessly using image plugins, allowing for swift and efficient customization. 

## Why Landscapist?

Landscapist is a thoughtfully designed solution, meticulously crafted to optimize image loading performance in Jetpack Compose. The majority of its composable functions are marked as **Restartable** and **Skippable**, signifying significant improvements in recomposition performance, as measured by the Compose compiler metrics. Additionally, the library's performance has been enhanced further through the implementation of [Baseline Profiles](https://android-developers.googleblog.com/2022/01/improving-app-performance-with-baseline.html).

Landscapist offers extensive support for pluggable features, making it a highly flexible and versatile library. Some of its remarkable capabilities include [ImageOptions](https://github.com/skydoves/landscapist#imageoptions),  [listening image state changes](https://github.com/skydoves/landscapist#listening-image-state-changes), and the ability to create [custom composables](https://github.com/skydoves/landscapist#custom-composables). It even offers seamless integration with [Android Studio for previewing](https://github.com/skydoves/landscapist#preview-on-android-studio), making development more efficient.

Furthermore, Landscapist provides various features such as [ImageComponent and ImagePlugin](https://github.com/skydoves/landscapist#imagecomponent-and-imageplugin), [placeholder](https://github.com/skydoves/landscapist#placeholder), [animations (circular reveal, crossfade)](https://github.com/skydoves/landscapist#animation), [transformation (blur)](https://github.com/skydoves/landscapist#transformation), and [palette](https://github.com/skydoves/landscapist#palette) support. With such a rich set of functionalities, Landscapist offers an unparalleled experience in image handling within Jetpack Compose.

!!! note "See the Compose compiler metrics for Landscapist"
    
    ![metrics](https://user-images.githubusercontent.com/24237865/201906004-f4490bdf-7af9-4ad6-b586-7dcc6f07d0c8.png)


## Who's using Landscapist?


Landscapist is experiencing incredible success, with over **+300,000** downloads every month from users worldwide! 🚀 

This impressive global reach reflects the trust and popularity of Landscapist among developers and demonstrates its significant impact on the Jetpack Compose image loading ecosystem.

![world](https://user-images.githubusercontent.com/24237865/196018576-a9c87534-81a2-4618-8519-0024b67964bf.png)

Especially, the global products below are using Landscapist.

| Product | Logo | License |
| ------- | ---- | ------- |
| [Twitter for Android](https://play.google.com/store/apps/details?id=com.twitter.android) | [![twitter](https://user-images.githubusercontent.com/24237865/125583182-9527dd48-433e-4e17-ae52-3f2bb544a847.jpg)](https://play.google.com/store/apps/details?id=com.twitter.android) | **[License](https://user-images.githubusercontent.com/24237865/125583736-f0ffa76f-8f87-433b-a9fd-192231dc5e63.jpg)** |
| [Azar for Android](https://play.google.com/store/apps/details?id=com.azarlive.android) | <img src="https://user-images.githubusercontent.com/24237865/155271118-2bbd5087-58b3-4360-a545-8fe4fc42efc8.jpg" width="62%" /> | **[License](https://user-images.githubusercontent.com/24237865/155270807-5edcab23-2690-4c05-a068-885ee5558b25.jpeg)** |
|[Hakuna: Live Streams and Chat](https://play.google.com/store/apps/details?id=com.movefastcompany.bora) | <img src="https://user-images.githubusercontent.com/24237865/218469230-64747182-cda3-443c-b90f-b43728d63ffa.png" width="160px" /> | License |
|[MISTPLAY: Play to earn rewards](https://play.google.com/store/apps/details?id=com.mistplay.mistplay) | <img src="https://github.com/skydoves/landscapist/assets/24237865/0693b2f6-174d-45cb-a84e-79e9839348db" width="160px" /> | License |
| [Faire for Android](https://play.google.com/store/apps/details?id=com.faire.retailer&hl=en_CA&gl=US) | <img src="https://user-images.githubusercontent.com/24237865/158280614-2740e38d-ca47-49f8-a493-3eb98d7e6b27.png" width="160px" /> | License |
| [Stream Chat & Video SDKs for Android](https://getstream.io/chat/sdk/android/) | <img src="https://github-production-user-asset-6210df.s3.amazonaws.com/24237865/340950946-205c9b9c-5665-423e-9ae0-49eab5c58829.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAVCODYLSA53PQK4ZA%2F20240619%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20240619T063711Z&X-Amz-Expires=300&X-Amz-Signature=ba7f4473d36cfe7ec27a3a0c902202844e04884716a303e280a5b416c7041a21&X-Amz-SignedHeaders=host&actor_id=0&key_id=0&repo_id=0" width="160px" /> | License |
