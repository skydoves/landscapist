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
package com.skydoves.landscapist.coil3

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import coil3.Image
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import com.skydoves.landscapist.plugins.ImagePlugin

/** Transforms coil's [Image] to Compose [ImageBitmap]. */
@Composable
internal expect fun Image.toImageBitmap(): ImageBitmap

/** Returns coil's platform context. */
internal expect val platformContext: PlatformContext

/** Returns [ImageLoader] from the platform context. */
internal expect val contextImageLoader: ImageLoader

/** Builds an [ImageRequest] depending on its target platform. */
@Composable
internal expect fun buildImageRequest(
  data: Any?,
  requestListener: ImageRequest.Listener?,
): ImageRequest

@Composable
internal expect fun rememberImagePainter(image: Image, imagePlugins: List<ImagePlugin>): Painter
