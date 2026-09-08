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

import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.createPlatformDecoder
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * The high water mark, which is what actually kills an app.
 *
 * Cumulative allocation understates a decoder badly. A decoder that materialises a full raster and
 * then throws it away allocates the same total as one that never made it, near enough, but only one
 * of the two needs the memory to exist at once. That peak is what an `OutOfMemoryError` is measured
 * against, and nothing here was reporting it.
 *
 * Three paths, because two of them are the same stack and the third is a different one:
 * landscapist as it decodes now, the same ImageIO stack made to decode at full size and scale down
 * afterwards, and Coil's Skia path. The first two are the honest before and after of subsampling.
 * The third decodes into native memory, so its Java heap row is close to zero and means nothing;
 * the resident set row is the one to read for it.
 */
internal fun peakMemoryComparison() {
  val photo = largePhoto()
  println(
    "peak memory decoding a 4000x3000 JPEG (${photo.size.toLong().formatBytes()}) down to 400x300",
  )

  val decoder = createPlatformDecoder()
  val config = LandscapistConfig()
  val paths = listOf<Pair<String, () -> Unit>>(
    "landscapist" to {
      runBlocking {
        val result = decoder.decode(photo, "image/jpeg", 400, 300, config)
        check(result is DecodeResult.Success) { "decode failed: $result" }
      }
    },
    "same stack, no subsampling" to { fullDecodeThenScale(photo) },
    "coil (skia)" to { skiaDecodeThenScale(photo) },
  )

  // Warm every path first. A cold ImageIO reader registry, a cold Skia codec table and a heap that
  // has not grown to its working size all land on whichever path runs first.
  for ((_, run) in paths) repeat(3) { run() }

  println("  peak Java heap above the resting heap, median of 5")
  for ((name, run) in paths) {
    val samples = LongArray(5) { peakHeapBytes { run() } }
    println("    ${name.padEnd(28)}${samples.median().formatBytes()}")
  }
  val photoFile = java.io.File.createTempFile("landscapist-benchmark", ".jpg")
  photoFile.deleteOnExit()
  photoFile.writeBytes(photo)
  println("  peak resident set of a fresh JVM that decodes once, median of 3")
  for ((name, _) in paths) {
    val samples = LongArray(3) { childPeakResident(name, photoFile.absolutePath) }
    println("    ${name.padEnd(28)}${samples.median().formatBytes()}")
  }
  println(
    "    Measured in a child process, because the Java heap cannot see a Skia decode at all. The " +
      "child reads the JPEG from disk, warms all three paths on a thumbnail so that loading the " +
      "ImageIO registry and the skiko native library lands in its baseline, says it is ready, " +
      "and decodes once on command while the parent watches its resident set.",
  )
  println(
    "    A zero means the decode fit inside memory the process had already been given, not that " +
      "it used none. The resident set only moves when a process has to ask the operating system " +
      "for more, so every row here is a floor. Without the thumbnail warm up the ordering " +
      "reversed, because loading skiko was being billed to the Skia row and loading the ImageIO " +
      "readers to landscapist's.",
  )
  println()
}

/**
 * Runs one decode path in a fresh JVM and reports how far its resident set rose to do it.
 *
 * The child reads the photo from a file rather than generating it, so it has never held a
 * forty eight megabyte raster for any other reason, and its baseline is honest.
 */
private fun childPeakResident(path: String, photoPath: String): Long {
  val process = ProcessBuilder(
    "${System.getProperty("java.home")}/bin/java",
    "-cp",
    System.getProperty("java.class.path"),
    "com.skydoves.landscapist.benchmark.EngineBenchmarkKt",
  ).also {
    it.environment()["LANDSCAPIST_DECODE_PATH"] = path
    it.environment()["LANDSCAPIST_DECODE_PHOTO"] = photoPath
  }.start()
  try {
    val out = process.inputStream.bufferedReader()
    val writer = process.outputStream.bufferedWriter()
    check(out.readLine() == "ready") { "child never became ready" }
    val baseline = residentBytes(process.pid())
    val peak = java.util.concurrent.atomic.AtomicLong(baseline)
    val running = java.util.concurrent.atomic.AtomicBoolean(true)
    val sampler = Thread {
      while (running.get()) {
        val rss = residentBytes(process.pid())
        if (rss > 0) peak.updateAndGet { if (rss > it) rss else it }
      }
    }
    sampler.isDaemon = true
    sampler.start()
    writer.write("go\n")
    writer.flush()
    val done = out.readLine()
    running.set(false)
    sampler.join()
    check(done == "done") { "child reported $done" }
    return peak.get() - baseline
  } finally {
    process.destroy()
    process.waitFor()
  }
}

/**
 * The child half of [childPeakResident]: read the photo, wait, decode once, say so.
 *
 * Called from `main` before anything else, so the process has done nothing but load classes.
 */
internal fun runDecodeChild(path: String, photoPath: String) {
  val photo = java.io.File(photoPath).readBytes()
  val decoder = createPlatformDecoder()
  val config = LandscapistConfig()
  // Every child warms every path on a thumbnail before it reports ready, so loading the ImageIO
  // plugin registry and the skiko native library land in the baseline rather than in whichever
  // path happens to be the one being measured. The thumbnail is small enough that the heap does
  // not grow to hide the real decode.
  val thumbnail = jpegBytes(64, 48)
  runBlocking { decoder.decode(thumbnail, "image/jpeg", 32, 24, config) }
  fullDecodeThenScale(thumbnail)
  skiaDecodeThenScale(thumbnail)
  System.gc()
  println("ready")
  System.out.flush()
  readLine()
  when (path) {
    "landscapist" -> runBlocking {
      val result = decoder.decode(photo, "image/jpeg", 400, 300, config)
      check(result is DecodeResult.Success) { "decode failed: $result" }
    }
    "same stack, no subsampling" -> fullDecodeThenScale(photo)
    else -> skiaDecodeThenScale(photo)
  }
  println("done")
  System.out.flush()
}

/** What the same stack costs without subsampling: the whole raster, then a scale. */
private fun fullDecodeThenScale(bytes: ByteArray) {
  val full = ImageIO.read(java.io.ByteArrayInputStream(bytes)) ?: error("decode failed")
  val scaled = BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB)
  val g = scaled.createGraphics()
  try {
    g.drawImage(full, 0, 0, 400, 300, null)
  } finally {
    g.dispose()
  }
  full.flush()
}

/** What `SkiaImageDecoder` does: a full decode, then a raster to scale into. */
private fun skiaDecodeThenScale(bytes: ByteArray) {
  val image = org.jetbrains.skia.Image.makeFromEncoded(bytes)
  val bitmap = org.jetbrains.skia.Bitmap()
  bitmap.allocN32Pixels(400, 300)
  org.jetbrains.skia.Canvas(bitmap).drawImageRect(
    image,
    org.jetbrains.skia.Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
    org.jetbrains.skia.Rect.makeWH(400f, 300f),
  )
  bitmap.setImmutable()
  image.close()
}

/** Resident set of one process in bytes, or -1 where the platform cannot be asked. */
private fun residentBytes(pid: Long): Long = runCatching {
  val process = ProcessBuilder("ps", "-o", "rss=", "-p", pid.toString())
    .redirectErrorStream(true)
    .start()
  val text = process.inputStream.bufferedReader().use { it.readText() }.trim()
  process.waitFor()
  text.toLong() * 1024
}.getOrDefault(-1L)

/** A JPEG with enough detail that the encoder cannot collapse it to nothing. */
private fun largePhoto(): ByteArray = jpegBytes(4000, 3000)

private fun jpegBytes(width: Int, height: Int): ByteArray {
  val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
  var seed = 0x9E3779B9.toInt()
  for (y in 0 until height step 2) {
    for (x in 0 until width step 2) {
      seed = seed * 1_664_525 + 1_013_904_223
      val rgb = seed and 0xFFFFFF
      image.setRGB(x, y, rgb)
      if (x + 1 < width) image.setRGB(x + 1, y, rgb)
      if (y + 1 < height) image.setRGB(x, y + 1, rgb)
      if (x + 1 < width && y + 1 < height) image.setRGB(x + 1, y + 1, rgb)
    }
  }
  return java.io.ByteArrayOutputStream().use { out ->
    ImageIO.write(image, "jpg", out)
    out.toByteArray()
  }
}
