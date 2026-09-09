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
package com.skydoves.landscapist.core.decoder

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.PixelGeometry
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.SurfaceProps
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * Decodes through Skia, when skiko is on the classpath.
 *
 * ImageIO's `setSourceSubsampling` never reaches libjpeg's own scaling, so asking for one pixel in
 * eight still runs the full inverse DCT. Skia's codec scales during it, so an eighth costs an
 * eighth.
 *
 * Skiko is `compileOnly`. Every Compose Multiplatform application resolves it; a plain JVM consumer
 * without it falls back to ImageIO.
 */
internal object SkiaJvmDecoder {

  private val surfaceProps by lazy { SurfaceProps(PixelGeometry.UNKNOWN) }

  /**
   * Whether skiko's native library really loads.
   *
   * The API jar can be present without the native runtime, which only fails when a Skia type is
   * touched, so this forces the load. Call it from behind `runCatching`.
   */
  fun isUsable(): Boolean = runCatching {
    Data.makeEmpty().close()
    true
  }.getOrDefault(false)

  /**
   * Decodes [data] to at most [targetWidth] by [targetHeight], or returns null when Skia cannot
   * read these bytes, so the caller can fall back to ImageIO.
   */
  fun decode(
    data: ByteArray,
    targetWidth: Int?,
    targetHeight: Int?,
    maxSize: Int,
  ): DecodeResult.Success? {
    if (data.isEmpty()) return null
    return runCatching { decodeWithSkia(data, targetWidth, targetHeight, maxSize) }.getOrNull()
  }

  private fun decodeWithSkia(
    data: ByteArray,
    targetWidth: Int?,
    targetHeight: Int?,
    maxSize: Int,
  ): DecodeResult.Success? {
    val encoded = Data.makeFromBytes(data)
    try {
      val codec = Codec.makeFromData(encoded)
      try {
        val originalWidth = codec.width
        val originalHeight = codec.height
        if (originalWidth <= 0 || originalHeight <= 0) return null
        // An animated image has to keep its frames, and that is the animation decoder's job.
        if (codec.frameCount > 1) return null

        val (finalWidth, finalHeight) = fitInside(
          originalWidth = originalWidth,
          originalHeight = originalHeight,
          targetWidth = targetWidth,
          targetHeight = targetHeight,
          maxSize = maxSize,
        )

        // Only a JPEG scales while decoding, so anything else is handed back for ImageIO to
        // subsample instead. Only when ImageIO can read it, or the same image would decode at one
        // size and fail at another.
        val shrinking = finalWidth < originalWidth || finalHeight < originalHeight
        if (shrinking &&
          codec.encodedImageFormat != EncodedImageFormat.JPEG &&
          imageIoCanRead(data)
        ) {
          return null
        }

        val decoded = readScaled(codec, originalWidth, originalHeight, finalWidth, finalHeight)
        try {
          val exact = if (decoded.width == finalWidth && decoded.height == finalHeight) {
            decoded
          } else {
            resample(decoded, finalWidth, finalHeight)
          }
          try {
            return DecodeResult.Success(
              bitmap = exact.toBufferedImage(),
              width = exact.width,
              height = exact.height,
            )
          } finally {
            if (exact !== decoded) exact.close()
          }
        } finally {
          decoded.close()
        }
      } finally {
        codec.close()
      }
    } finally {
      encoded.close()
    }
  }

  /**
   * Reads the codec at the smallest size it supports that still covers [finalWidth] by
   * [finalHeight], falling back to the full size when it supports nothing smaller.
   *
   * libjpeg scales by eighths, so the candidate is the smallest eighth still large enough. Every
   * other codec refuses before any pixel is decoded.
   */
  private fun readScaled(
    codec: Codec,
    originalWidth: Int,
    originalHeight: Int,
    finalWidth: Int,
    finalHeight: Int,
  ): Bitmap {
    if (codec.encodedImageFormat == EncodedImageFormat.JPEG) {
      val eighths = eighthsCovering(originalWidth, originalHeight, finalWidth, finalHeight)
      if (eighths < 8) {
        val sampled = allocate(scaledUp(originalWidth, eighths), scaledUp(originalHeight, eighths))
        val read = runCatching { codec.readPixels(sampled) }.isSuccess
        if (read) return sampled
        sampled.close()
      }
    }
    val full = allocate(originalWidth, originalHeight)
    return try {
      full.also { codec.readPixels(it) }
    } catch (throwable: Throwable) {
      // Truncated bytes are ordinary, and the pixels are off heap, so they are freed here.
      full.close()
      throw throwable
    }
  }

  /** The smallest eighth of the source that still covers the requested box. */
  private fun eighthsCovering(
    originalWidth: Int,
    originalHeight: Int,
    finalWidth: Int,
    finalHeight: Int,
  ): Int {
    if (finalWidth <= 0 || finalHeight <= 0) return 8
    for (eighths in 1..7) {
      if (scaledUp(originalWidth, eighths) >= finalWidth &&
        scaledUp(originalHeight, eighths) >= finalHeight
      ) {
        return eighths
      }
    }
    return 8
  }

  /** Whether ImageIO has a reader for these bytes, and so can subsample them itself. */
  private fun imageIoCanRead(data: ByteArray): Boolean = runCatching {
    ImageIO.createImageInputStream(ByteArrayInputStream(data)).use { stream ->
      ImageIO.getImageReaders(stream).hasNext()
    }
  }.getOrDefault(false)

  /**
   * The size libjpeg produces for [eighths] of [dimension].
   *
   * Rounded up, because that is what libjpeg does (`jdiv_round_up`) and because SkJpegCodec accepts
   * a request only when it matches exactly. Asking for the rounded down size instead is refused for
   * every dimension that is not a multiple of eight, which is most photographs, and the decode then
   * falls back to a full size read: slower than the subsampling this replaced, and holding the
   * whole raster that subsampling never materialised.
   *
   * Internal rather than private so the test that proves Skia accepts these sizes asks for them the
   * way the decoder does, instead of carrying its own copy of the rounding that would go on passing
   * after the rounding here was changed.
   */
  internal fun scaledUp(dimension: Int, eighths: Int): Int = (dimension * eighths + 7) / 8

  private fun allocate(width: Int, height: Int): Bitmap = Bitmap().apply {
    // Premultiplied, because a Skia canvas will only draw into premultiplied pixels. The readback
    // undoes it, since a TYPE_INT_ARGB raster is unpremultiplied.
    // sRGB explicitly. The four argument ImageInfo leaves the colour space null, which tells Skia
    // to apply no transform, so an ICC tagged photograph came back oversaturated against the
    // ImageIO path that converts it. Measured at 26 of 255 on a Display P3 capture.
    allocPixels(ImageInfo(width, height, ColorType.N32, ColorAlphaType.PREMUL, ColorSpace.sRGB))
  }

  /**
   * Scales [source] to exactly [width] by [height].
   *
   * Mitchell rather than a plain bilinear tap: the sampled read above is only ever within an eighth
   * of the target for JPEG, but a PNG or a WebP arrives at full size and a single bilinear pass
   * down to a thumbnail aliases badly. This is the resampler Coil's own Skia decoder uses.
   */
  private fun resample(source: Bitmap, width: Int, height: Int): Bitmap {
    val target = allocate(width, height)
    try {
      val canvas = Canvas(target, surfaceProps)
      try {
        // Marked immutable first: makeFromBitmap copies the whole raster from a mutable bitmap,
        // which for a full size read is a second copy of the source.
        source.setImmutable()
        val image = Image.makeFromBitmap(source)
        try {
          canvas.drawImageRect(
            image,
            Rect.makeWH(source.width.toFloat(), source.height.toFloat()),
            Rect.makeWH(width.toFloat(), height.toFloat()),
            SamplingMode.MITCHELL,
            null,
            true,
          )
        } finally {
          image.close()
        }
      } finally {
        canvas.close()
      }
    } catch (throwable: Throwable) {
      target.close()
      throw throwable
    }
    return target
  }

  /**
   * Copies the pixels into a [BufferedImage], which is what every desktop consumer of the decoder
   * already expects to receive.
   */
  private fun Bitmap.toBufferedImage(): BufferedImage {
    val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
    val pixels = requireNotNull(readPixels(info, width * 4, 0, 0)) {
      "Skia returned no pixels for a ${width}x$height bitmap"
    }
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val raster = (image.raster.dataBuffer as DataBufferInt).data
    var byteIndex = 0
    for (index in raster.indices) {
      // BGRA in memory order, so the alpha byte is last and blue is first.
      raster[index] = (pixels[byteIndex + 3].toInt() and 0xFF shl 24) or
        (pixels[byteIndex + 2].toInt() and 0xFF shl 16) or
        (pixels[byteIndex + 1].toInt() and 0xFF shl 8) or
        (pixels[byteIndex].toInt() and 0xFF)
      byteIndex += 4
    }
    return image
  }
}
