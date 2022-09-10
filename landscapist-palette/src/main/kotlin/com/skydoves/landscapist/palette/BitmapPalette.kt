/*
 * Designed and developed by 2020-2022 skydoves (Jaewoong Eum)
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

import android.graphics.Bitmap
import android.util.LruCache
import androidx.palette.graphics.Palette

/**
 * BitmapPalette is a [Palette] generator for extracting major (theme) colors
 * from an image.
 *
 * @param imageModel A target image url data for extracting colors.
 * @param useCache A flag for using local cache for loaded palette colors.
 * @param interceptor A custom interceptor before generating colors.
 * @param paletteLoadedListener A listener for listening to the loaded palette.
 */
internal class BitmapPalette constructor(
  private var imageModel: Any? = null,
  private val useCache: Boolean = true,
  private val interceptor: PaletteBuilderInterceptor? = null,
  private val paletteLoadedListener: PaletteLoadedListener? = null
) {

  fun applyImageModel(imageModel: Any?): BitmapPalette = apply {
    this.imageModel = imageModel
  }

  fun generate(bitmap: Bitmap?) {
    val target = bitmap ?: return
    val model = imageModel ?: return
    if (useCache) {
      val palette = cache.get(model)
      if (palette != null) {
        paletteLoadedListener?.onPaletteLoaded(palette)
        return
      }
    }
    val builder = interceptor?.intercept(Palette.Builder(target))
      ?: Palette.Builder(target)
    builder.generate async@{
      val palette: Palette = it ?: return@async
      if (useCache) {
        cache.put(model, palette)
      }
      paletteLoadedListener?.onPaletteLoaded(palette)
    }
  }

  internal companion object {
    internal val cache: LruCache<Any, Palette?>
      by lazy(LazyThreadSafetyMode.NONE) { LruCache(20) }
  }
}
