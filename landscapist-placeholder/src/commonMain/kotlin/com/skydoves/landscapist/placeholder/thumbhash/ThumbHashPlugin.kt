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
package com.skydoves.landscapist.placeholder.thumbhash

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
 * A plugin that displays a ThumbHash placeholder while the image is loading.
 *
 * ThumbHash is a more modern alternative to BlurHash with several advantages:
 * - Preserves the aspect ratio of the original image
 * - Supports alpha channel (transparency)
 * - Better color accuracy
 * - Smaller encoded size (~25 bytes on average)
 *
 * Usage with Base64 string:
 * ```kotlin
 * CoilImage(
 *   imageModel = { imageUrl },
 *   component = rememberImageComponent {
 *     +ThumbHashPlugin.fromBase64("1QcSHQRnh493V4dIh4eXh1h4kJUI")
 *   }
 * )
 * ```
 *
 * Usage with byte array:
 * ```kotlin
 * CoilImage(
 *   imageModel = { imageUrl },
 *   component = rememberImageComponent {
 *     +ThumbHashPlugin(thumbHash = thumbHashBytes)
 *   }
 * )
 * ```
 *
 * @param thumbHash The ThumbHash as a byte array.
 */
public class ThumbHashPlugin(
  private val thumbHash: ByteArray,
) : ImagePlugin.LoadingStatePlugin {

  @Composable
  override fun compose(
    modifier: Modifier,
    imageOptions: ImageOptions,
    executor: @Composable (IntSize) -> Unit,
  ): ImagePlugin = apply {
    val imageBitmap = remember(thumbHash) {
      decodeThumbHashToImageBitmap(thumbHash)
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

  public companion object {
    /**
     * Creates a ThumbHashPlugin from a Base64-encoded string.
     *
     * @param base64 The ThumbHash as a Base64 string.
     * @return A ThumbHashPlugin, or null if the Base64 string is invalid.
     */
    public fun fromBase64(base64: String): ThumbHashPlugin? {
      val bytes = base64Decode(base64) ?: return null
      return ThumbHashPlugin(bytes)
    }

    private fun base64Decode(input: String): ByteArray? {
      val base64Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
      val cleanInput = input.replace("=", "").replace("\n", "").replace("\r", "")

      if (cleanInput.isEmpty()) return null

      val outputLength = cleanInput.length * 3 / 4
      val output = ByteArray(outputLength)

      var outputIndex = 0
      var buffer = 0
      var bitsCollected = 0

      for (c in cleanInput) {
        val value = base64Chars.indexOf(c)
        if (value < 0) return null

        buffer = (buffer shl 6) or value
        bitsCollected += 6

        if (bitsCollected >= 8) {
          bitsCollected -= 8
          if (outputIndex < output.size) {
            output[outputIndex++] = (buffer shr bitsCollected).toByte()
          }
          buffer = buffer and ((1 shl bitsCollected) - 1)
        }
      }

      return output.copyOf(outputIndex)
    }
  }
}

/**
 * Decodes a ThumbHash byte array to an ImageBitmap.
 * This is a platform-specific function implemented in androidMain and skiaMain.
 */
internal expect fun decodeThumbHashToImageBitmap(thumbHash: ByteArray): ImageBitmap?
