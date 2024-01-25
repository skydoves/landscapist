/*
 * Designed and developed by 2020-2023 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("unused")

package com.skydoves.benchmark.landscapist.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.NetworkFetcher
import coil3.network.ktor.KtorNetworkFetcherFactory
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.backends.okhttp3.OkHttpImagePipelineConfigFactory
import okhttp3.OkHttpClient

class App : Application(), SingletonImageLoader.Factory {

  override fun onCreate() {
    super.onCreate()

    // initializes an image pipeline for Fresco only.
    val pipelineConfig =
      OkHttpImagePipelineConfigFactory
        .newBuilder(this, OkHttpClient.Builder().build())
        .setDiskCacheEnabled(true)
        .setDownsampleEnabled(true)
        .setResizeAndRotateEnabledForNetwork(true)
        .build()

    Fresco.initialize(this, pipelineConfig)
  }

  override fun newImageLoader(context: Context): ImageLoader {
    return ImageLoader.Builder(context)
      .components {
        add(KtorNetworkFetcherFactory())
      }
      .build()
  }
}
