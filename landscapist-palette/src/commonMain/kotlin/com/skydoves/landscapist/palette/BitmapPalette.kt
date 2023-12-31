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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.palette.graphics.Palette
import com.kmpalette.rememberPaletteState

/**
 * BitmapPalette is a [Palette] generator for extracting major (theme) colors
 * from an image.
 *
 * @param imageModel A target image url data for extracting colors.
 * @param useCache A flag for using local cache for loaded palette colors.
 * @param interceptor A custom interceptor before generating colors.
 * @param paletteLoadedListener A listener for listening to the loaded palette.
 */
internal class BitmapPalette(
  private var imageModel: Any? = null,
  private val useCache: Boolean = true,
  private val interceptor: PaletteBuilderInterceptor? = null,
  private val paletteLoadedListener: PaletteLoadedListener? = null,
) {

  fun applyImageModel(imageModel: Any?): BitmapPalette = apply {
    this.imageModel = imageModel
  }

  @Composable
  fun generate(bitmap: ImageBitmap) {
    val cacheSize = if (useCache) 5 else 0
    val paletteState = rememberPaletteState(cacheSize = cacheSize)
    LaunchedEffect(bitmap) {
      paletteState.generate(bitmap)
      val palette = paletteState.palette

      if (palette != null) {
        paletteLoadedListener?.onPaletteLoaded(palette)
      }
    }
  }
}
