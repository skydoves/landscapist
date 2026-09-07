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
package com.skydoves.landscapist.svg

import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.ImageDecoder
import com.skydoves.landscapist.core.decoder.createPlatformDecoder
import com.skydoves.landscapist.core.decoder.isSvg
import com.skydoves.landscapist.core.decoder.readSvgDimensions

/**
 * Rasterizes SVG markup and hands every other format to [delegate].
 *
 * Install it on the loader to load SVG from a URL:
 *
 * ```kotlin
 * Landscapist.setInstance(
 *   Landscapist.builder()
 *     .decoder(SvgImageDecoder())
 *     .build(),
 * )
 * ```
 *
 * The SVG is rendered at the size the request asks for, which is the size the composable was
 * measured at, so it is as sharp as the layout it lands in. A larger request re-renders rather than
 * upscaling, because the target size is part of the cache key.
 *
 * Markup that fails to parse is reported as [DecodeResult.Error], so the composable's failure slot
 * runs the same way it does for a corrupt bitmap.
 *
 * @property delegate The decoder that handles everything that is not SVG.
 */
public class SvgImageDecoder(
  private val delegate: ImageDecoder = createPlatformDecoder(),
) : ImageDecoder {

  override suspend fun decode(
    data: ByteArray,
    mimeType: String?,
    targetWidth: Int?,
    targetHeight: Int?,
    config: LandscapistConfig,
  ): DecodeResult {
    if (!isSvg(data, mimeType)) {
      return delegate.decode(data, mimeType, targetWidth, targetHeight, config)
    }

    val intrinsic = readSvgDimensions(data)
    val size = svgRenderSize(
      intrinsicWidth = intrinsic?.width,
      intrinsicHeight = intrinsic?.height,
      targetWidth = targetWidth,
      targetHeight = targetHeight,
      maxSize = config.maxBitmapSize,
    )
    return rasterizeSvg(data, size.width, size.height)
  }
}

/** The pixel size an SVG is rendered at. */
internal data class SvgRenderSize(val width: Int, val height: Int)

/**
 * Picks the pixel size to render at.
 *
 * The request's target size wins, since that is what the composable was measured at. Often only one
 * bound is real, so the other follows the document's aspect ratio rather than stretching it.
 * Everything is clamped to [maxSize], and a document that declares nothing falls back to
 * [DEFAULT_SVG_SIZE] rather than rendering at an arbitrary scale.
 */
internal fun svgRenderSize(
  intrinsicWidth: Int?,
  intrinsicHeight: Int?,
  targetWidth: Int?,
  targetHeight: Int?,
  maxSize: Int,
): SvgRenderSize {
  val declaredWidth = intrinsicWidth?.takeIf { it > 0 }
  val declaredHeight = intrinsicHeight?.takeIf { it > 0 }
  val aspectRatio = if (declaredWidth != null && declaredHeight != null) {
    declaredWidth.toDouble() / declaredHeight.toDouble()
  } else {
    null
  }

  val width = targetWidth?.takeIf { it.isRealBound() }
  val height = targetHeight?.takeIf { it.isRealBound() }

  val resolvedWidth = width
    ?: height?.let { h -> aspectRatio?.let { (h * it).toInt() } }
    ?: declaredWidth
    ?: DEFAULT_SVG_SIZE
  val resolvedHeight = height
    ?: width?.let { w -> aspectRatio?.let { (w / it).toInt() } }
    ?: declaredHeight
    ?: DEFAULT_SVG_SIZE

  return SvgRenderSize(
    width = resolvedWidth.coerceIn(1, maxSize),
    height = resolvedHeight.coerceIn(1, maxSize),
  )
}

// Int.MAX_VALUE is how an unbounded layout dimension reaches the decoder.
private fun Int.isRealBound(): Boolean = this > 0 && this != Int.MAX_VALUE

/** Rendering size for a document that declares neither a size nor a view box. */
internal const val DEFAULT_SVG_SIZE: Int = 512

/**
 * Renders [data] into a platform bitmap of [width] by [height] pixels, or returns
 * [DecodeResult.Error] when the markup cannot be parsed.
 */
internal expect fun rasterizeSvg(data: ByteArray, width: Int, height: Int): DecodeResult
