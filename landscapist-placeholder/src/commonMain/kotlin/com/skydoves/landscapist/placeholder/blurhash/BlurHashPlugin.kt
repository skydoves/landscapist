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
package com.skydoves.landscapist.placeholder.blurhash

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.IntSize
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.plugins.ImagePlugin

/**
 * A plugin that displays a BlurHash placeholder while the image is loading.
 *
 * BlurHash is a compact representation of a placeholder for an image that can be
 * decoded instantly into a blurred preview. This provides a better user experience
 * than showing a blank space or generic placeholder.
 *
 * Usage:
 * ```kotlin
 * CoilImage(
 *   imageModel = { imageUrl },
 *   component = rememberImageComponent {
 *     +BlurHashPlugin(
 *       blurHash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj",
 *       width = 32,
 *       height = 32
 *     )
 *   }
 * )
 * ```
 *
 * @param blurHash The BlurHash string to decode.
 * @param width The width of the decoded placeholder image. Default is 32.
 * @param height The height of the decoded placeholder image. Default is 32.
 * @param punch The contrast multiplier. Higher values = more contrast. Default is 1.0.
 */
public class BlurHashPlugin(
  private val blurHash: String,
  private val width: Int = 32,
  private val height: Int = 32,
  private val punch: Float = 1.0f,
) : ImagePlugin.LoadingStatePlugin {

  @Composable
  override fun compose(
    modifier: Modifier,
    imageOptions: ImageOptions,
    executor: @Composable (IntSize) -> Unit,
  ): ImagePlugin = apply {
    val imageBitmap = remember(blurHash, width, height, punch) {
      decodeBlurHashToImageBitmap(blurHash, width, height, punch)
    }

    if (imageBitmap != null) {
      Image(
        painter = BitmapPainter(imageBitmap),
        contentDescription = imageOptions.contentDescription,
        modifier = modifier,
        alignment = imageOptions.alignment,
        contentScale = imageOptions.contentScale,
        alpha = imageOptions.alpha,
        colorFilter = imageOptions.colorFilter,
      )
    }
  }
}

/**
 * Decodes a BlurHash string to an ImageBitmap.
 * This is a platform-specific function implemented in androidMain and skiaMain.
 */
internal expect fun decodeBlurHashToImageBitmap(
  blurHash: String,
  width: Int,
  height: Int,
  punch: Float,
): ImageBitmap?
