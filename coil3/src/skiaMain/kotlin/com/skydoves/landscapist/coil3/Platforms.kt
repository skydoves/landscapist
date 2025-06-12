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
@file:OptIn(ExperimentalCoilApi::class)

package com.skydoves.landscapist.coil3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import coil3.Image
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.NetworkFetcher
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.toBitmap
import com.skydoves.landscapist.plugins.ImagePlugin
import com.skydoves.landscapist.plugins.composePainterPlugins

internal actual fun getPlatform(): Platform = Platform.NonAndroid

@Composable
internal actual fun Image.toImageBitmap(): ImageBitmap {
  return toBitmap().asComposeImageBitmap()
}

internal actual val platformContext: PlatformContext
  @Composable get() = PlatformContext.INSTANCE

internal actual val platformImageLoader: ImageLoader
  @Composable get() = SingletonImageLoader.get(platformContext)

@Composable
internal actual fun buildImageRequest(
  data: Any?,
  requestListener: ImageRequest.Listener?,
): ImageRequest {
  return ImageRequest.Builder(platformContext)
    .data(data)
    .listener(requestListener)
    .build()
}

@Composable
internal actual fun rememberImagePainter(image: Image, imagePlugins: List<ImagePlugin>): Painter {
  val bitmapPainter = bitmapPainter(image = image)
  return remember(image, imagePlugins) {
    bitmapPainter
  }.composePainterPlugins(
    imagePlugins = imagePlugins,
    imageBitmap = image.toImageBitmap(),
  )
}

@Composable
internal fun bitmapPainter(image: Image): Painter {
  return BitmapPainter(image = image.toImageBitmap())
}

internal actual val networkFetcherFactory: NetworkFetcher.Factory = KtorNetworkFetcherFactory()
