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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.graphics.drawable.toBitmap
import coil3.Image
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.lifecycle
import com.skydoves.landscapist.plugins.ImagePlugin
import com.skydoves.landscapist.rememberDrawablePainter

@Composable
internal actual fun Image.toImageBitmap(): ImageBitmap {
  val context = LocalContext.current
  return asDrawable(context.resources).toBitmap().asImageBitmap()
}

internal actual val platformContext: PlatformContext
  @Composable get() = LocalContext.current

internal actual val contextImageLoader: ImageLoader
  @Composable get() = platformContext.imageLoader

@Composable
internal actual fun buildImageRequest(
  data: Any?,
  requestListener: ImageRequest.Listener?,
): ImageRequest {
  val lifecycleOwner = LocalLifecycleOwner.current
  return ImageRequest.Builder(platformContext)
    .data(data)
    .listener(requestListener)
    .lifecycle(lifecycleOwner)
    .build()
}

@Composable
internal actual fun rememberImagePainter(image: Image, imagePlugins: List<ImagePlugin>): Painter {
  val resource = platformContext.resources
  return rememberDrawablePainter(drawable = image.asDrawable(resource), imagePlugins = imagePlugins)
}
