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
package com.skydoves.landscapist.benchmark

import coil3.PlatformContext
import coil3.decode.ImageSource
import coil3.decode.SkiaImageDecoder
import coil3.request.Options
import coil3.size.Precision
import coil3.size.Scale
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.createPlatformDecoder
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.FileSystem
import coil3.size.Size as CoilSize

/**
 * Each library's real decoder against the same bytes. Coil's row runs [SkiaImageDecoder], which is
 * the decoder its JVM `ImageLoader` installs; the row used to be a hand written Skia snippet
 * labelled "coil (skia)" that Coil itself never executed.
 *
 * Reported apart from the engine numbers because Coil decodes through Skia and landscapist-core
 * through ImageIO.
 */
internal fun decodeComparison() {
  val photo = jpegBytes(PHOTO_WIDTH, PHOTO_HEIGHT)
  println(
    "decode a ${PHOTO_WIDTH}x$PHOTO_HEIGHT JPEG (${photo.size.toLong().formatBytes()}) down to " +
      "${TARGET_WIDTH}x$TARGET_HEIGHT",
  )

  val decoder = createPlatformDecoder()
  val config = LandscapistConfig()

  val (landscapist, coil) = measurePaired(
    firstLabel = "landscapist",
    secondLabel = "coil decoder",
    warmups = 5,
    iterations = 25,
    first = {
      runBlocking {
        val result = decoder.decode(photo, "image/jpeg", TARGET_WIDTH, TARGET_HEIGHT, config)
        check(result is DecodeResult.Success) { "decode failed: $result" }
      }
    },
    second = { coilDecode(photo) },
  )

  Metrics.record("decode.landscapist.ns", landscapist.p50)
  Metrics.record("decode.coil.ns", coil.p50)
  report("decode ${PHOTO_WIDTH}x$PHOTO_HEIGHT -> ${TARGET_WIDTH}x$TARGET_HEIGHT", landscapist, coil)

  // Coil's decoder consumes its source, so neither the source nor the decoder can be lifted out of
  // the loop the way landscapist's decoder can. That leaves an okio copy of the encoded bytes and
  // two objects inside Coil's column and not inside landscapist's, which is a difference between
  // the arms rather than between the decoders. Measured rather than left unremarked, so the row is
  // read with it in hand.
  val coilSetup = measure("coil source and decoder", warmups = 5, iterations = 25) {
    val source = ImageSource(Buffer().write(photo), FileSystem.SYSTEM)
    try {
      SkiaImageDecoder(
        source = source,
        options = Options(
          context = PlatformContext.INSTANCE,
          size = CoilSize(TARGET_WIDTH, TARGET_HEIGHT),
          scale = Scale.FIT,
          precision = Precision.INEXACT,
        ),
      )
    } finally {
      source.close()
    }
  }
  println(
    "    Coil's column carries ${coilSetup.p50.formatNanos()} of source and decoder construction " +
      "that landscapist's does not, because its decoder consumes the source and cannot be reused.",
  )

  val landscapistBytes = allocatedBytes {
    runBlocking { decoder.decode(photo, "image/jpeg", TARGET_WIDTH, TARGET_HEIGHT, config) }
  }
  val coilBytes = allocatedBytes { coilDecode(photo) }
  // Measured rather than asserted: this row used to state a hardcoded 48 MB for the raster
  // landscapist no longer materialises, as if that number had come from a measurement.
  val fullRasterBytes = allocatedBytes { fullDecodeThenScale(photo) }
  println("  Java heap allocated for one decode")
  println("    landscapist                 ${landscapistBytes.formatBytes()}")
  println("    coil decoder                ${coilBytes.formatBytes()}")
  println("    landscapist, no subsampling ${fullRasterBytes.formatBytes()}")
  println(
    "    The first two rows are not like for like. Skia decodes into native memory, which the " +
      "Java heap cannot see, so Coil's row is only its JVM side while landscapist's is the whole " +
      "thing. The third row is the same ImageIO stack without subsampling, which is the raster " +
      "landscapist does not materialise.",
  )
  println()
}

/** One decode through the decoder Coil's JVM `ImageLoader` installs by default. */
internal fun coilDecode(photo: ByteArray) {
  val source = ImageSource(Buffer().write(photo), FileSystem.SYSTEM)
  try {
    val decoder = SkiaImageDecoder(
      source = source,
      options = Options(
        context = PlatformContext.INSTANCE,
        size = CoilSize(TARGET_WIDTH, TARGET_HEIGHT),
        scale = Scale.FIT,
        // AsyncImagePainter forces INEXACT when precision is undefined, so this is what users run.
        precision = Precision.INEXACT,
      ),
    )
    val result = runBlocking { decoder.decode() }
    check(result.image.height > 0) { "coil decoded nothing" }
  } finally {
    source.close()
  }
}

internal const val PHOTO_WIDTH = 4000
internal const val PHOTO_HEIGHT = 3000
internal const val TARGET_WIDTH = 400
internal const val TARGET_HEIGHT = 300
