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
package com.skydoves.landscapist.palette

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.plugins.ImagePlugin

/**
 * An image plugin that extends [ImagePlugin.SuccessStatePlugin] to be executed after loading image successfully.
 *
 * @property imageModel A target image url data for extracting colors.
 * @property useCache A flag for using local cache for loaded palette colors.
 * @property interceptor A custom interceptor before generating colors.
 * @property paletteLoadedListener A listener for listening to the loaded palette.
 */
@Immutable
public data class PalettePlugin(
  private val imageModel: Any? = null,
  private val useCache: Boolean = true,
  private val interceptor: PaletteBuilderInterceptor? = null,
  private val paletteLoadedListener: PaletteLoadedListener? = null
) : ImagePlugin.SuccessStatePlugin {

  private val bitmapPalette = BitmapPalette(
    imageModel = imageModel,
    useCache = useCache,
    interceptor = interceptor,
    paletteLoadedListener = paletteLoadedListener
  )

  @Composable
  override fun compose(
    modifier: Modifier,
    imageModel: Any?,
    imageOptions: ImageOptions?,
    imageBitmap: ImageBitmap?
  ): ImagePlugin =
    apply {
      imageBitmap?.let {
        bitmapPalette.applyImageModel(this.imageModel ?: imageModel)
        bitmapPalette.generate(it.asAndroidBitmap())
      }
    }
}
