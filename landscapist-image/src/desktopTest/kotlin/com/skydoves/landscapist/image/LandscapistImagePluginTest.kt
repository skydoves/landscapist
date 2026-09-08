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
package com.skydoves.landscapist.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.animation.circular.CircularRevealPlugin
import com.skydoves.landscapist.components.ImageComponent
import com.skydoves.landscapist.components.ImagePluginComponent
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.ImageDecoder
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
import com.skydoves.landscapist.plugins.ImagePlugin
import com.skydoves.landscapist.zoomable.ZoomablePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Every kind of [ImagePlugin], through the composable a user would install it in. */
class LandscapistImagePluginTest {

  private val url = "https://example.com/photo.png"
  private val imageSize = 40
  private val sceneSize = 40

  private fun sourceImage(): ImageBitmap {
    val bitmap = ImageBitmap(imageSize, imageSize)
    val bounds = Rect(Offset.Zero, Size(imageSize.toFloat(), imageSize.toFloat()))
    Canvas(bitmap).drawRect(bounds, Paint().apply { color = Color.Red })
    return bitmap
  }

  private inner class StubFetcher(private val hang: Boolean = false) : ImageFetcher {
    override fun canHandle(model: Any?): Boolean = true
    override suspend fun fetch(request: ImageRequest): FetchResult {
      if (hang) awaitCancellation()
      return FetchResult.Success(data = byteArrayOf(1, 2, 3, 4), mimeType = "image/png")
    }
  }

  private inner class StubDecoder : ImageDecoder {
    override suspend fun decode(
      data: ByteArray,
      mimeType: String?,
      targetWidth: Int?,
      targetHeight: Int?,
      config: LandscapistConfig,
    ): DecodeResult = DecodeResult.Success(sourceImage(), imageSize, imageSize)
  }

  /** A loader whose image is already in memory, so the first frame can draw it. */
  private fun warmLoader(): Landscapist {
    val loader = Landscapist.builder().noDiskCache().fetcher(
      StubFetcher(),
    ).decoder(StubDecoder()).build()
    runBlocking {
      loader.load(
        ImageRequest.builder().model(url).diskCachePolicy(CachePolicy.DISABLED).build(),
      ).first { it is ImageResult.Success }
    }
    return loader
  }

  /** A loader that never resolves, so the loading state stays on screen. */
  private fun hangingLoader(): Landscapist =
    Landscapist.builder().noDiskCache().fetcher(
      StubFetcher(hang = true),
    ).decoder(StubDecoder()).build()

  /** Renders one frame at [nanos] and returns its pixels as ARGB, row major. */
  private fun render(nanos: Long = 0L, content: @Composable () -> Unit): IntArray {
    val scene = ImageComposeScene(
      width = sceneSize,
      height = sceneSize,
      density = Density(1f),
      coroutineContext = Dispatchers.Unconfined,
      content = content,
    )
    try {
      val image = scene.render(nanos)
      try {
        val bytes = org.jetbrains.skia.Bitmap().use { bitmap ->
          bitmap.allocN32Pixels(image.width, image.height)
          check(image.readPixels(bitmap, 0, 0)) { "could not read the frame back" }
          bitmap.readPixels() ?: error("no pixels")
        }
        return IntArray(sceneSize * sceneSize) { index ->
          val offset = index * 4
          (bytes[offset + 3].toInt() and 0xFF shl 24) or
            (bytes[offset + 2].toInt() and 0xFF shl 16) or
            (bytes[offset + 1].toInt() and 0xFF shl 8) or
            (bytes[offset].toInt() and 0xFF)
        }
      } finally {
        image.close()
      }
    } finally {
      scene.close()
    }
  }

  /** Renders [count] frames of one scene and returns the last, since a cold load needs two. */
  private fun renderFrames(count: Int, content: @Composable () -> Unit): IntArray {
    val scene = ImageComposeScene(
      width = sceneSize,
      height = sceneSize,
      density = Density(1f),
      coroutineContext = Dispatchers.Unconfined,
      content = content,
    )
    try {
      var pixels = IntArray(sceneSize * sceneSize)
      repeat(count) { frame ->
        val image = scene.render(frame.toLong() * 16_000_000)
        try {
          val bytes = org.jetbrains.skia.Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(image.width, image.height)
            check(image.readPixels(bitmap, 0, 0)) { "could not read the frame back" }
            bitmap.readPixels() ?: error("no pixels")
          }
          pixels = IntArray(sceneSize * sceneSize) { index ->
            val offset = index * 4
            (bytes[offset + 3].toInt() and 0xFF shl 24) or
              (bytes[offset + 2].toInt() and 0xFF shl 16) or
              (bytes[offset + 1].toInt() and 0xFF shl 8) or
              (bytes[offset].toInt() and 0xFF)
          }
        } finally {
          image.close()
        }
      }
      return pixels
    } finally {
      scene.close()
    }
  }

  private fun IntArray.coverage(): Double = count { it ushr 24 != 0 }.toDouble() / size

  /** A component holding exactly [plugins], built without a composition. */
  private fun component(vararg plugins: ImagePlugin): ImageComponent =
    ImagePluginComponent(plugins.toMutableList())

  private fun image(
    loader: Landscapist,
    component: ImageComponent,
    success: @Composable (BoxScope.(LandscapistImageState.Success, Painter) -> Unit)? = null,
  ): @Composable () -> Unit = {
    LandscapistImage(
      imageModel = { url },
      landscapist = loader,
      component = component,
      modifier = Modifier.size(sceneSize.dp),
      requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
      success = success,
    )
  }

  @Test
  fun `a painter plugin reaches the drawing on the container path`() {
    // No slot and no wrapping plugin, so the container node draws the image itself.
    val loader = warmLoader()
    var used = false
    val plugin = object : ImagePlugin.PainterPlugin {
      @Composable
      override fun compose(imageBitmap: ImageBitmap, painter: Painter): Painter {
        used = true
        return ColorPainter(Color.Blue)
      }
    }

    val pixels = render {
      LandscapistImage(
        imageModel = { url },
        landscapist = loader,
        component = component(plugin),
        modifier = Modifier.size(sceneSize.dp),
        requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
      )
    }

    assertTrue(used, "the painter plugin was never composed")
    assertEquals(0xFF0000FF.toInt(), pixels[pixels.size / 2], "the plugin's painter was not drawn")
  }

  @Test
  fun `a circular reveal still animates on the container path`() {
    // It animates by reading state as it draws, so the container's paint must invalidate too.
    val loader = warmLoader()
    val component = component(CircularRevealPlugin(duration = 200))
    val scene = ImageComposeScene(
      width = sceneSize,
      height = sceneSize,
      density = Density(1f),
      coroutineContext = Dispatchers.Unconfined,
      content = image(loader, component),
    )
    val coverage = try {
      listOf(1L, 20L * 1_000_000, 400L * 1_000_000, 800L * 1_000_000).map { nanos ->
        val frame = scene.render(nanos)
        try {
          val bytes = org.jetbrains.skia.Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(frame.width, frame.height)
            check(frame.readPixels(bitmap, 0, 0))
            bitmap.readPixels() ?: error("no pixels")
          }
          var painted = 0
          var i = 0
          while (i + 3 < bytes.size) {
            if (bytes[i + 3] != 0.toByte()) painted++
            i += 4
          }
          painted.toDouble() / (frame.width * frame.height)
        } finally {
          frame.close()
        }
      }
    } finally {
      scene.close()
    }

    assertTrue(
      coverage.first() < 0.99,
      "the reveal was already complete on its first frame, so it never animated: $coverage",
    )
    assertTrue(
      coverage.last() > coverage.first(),
      "the reveal never grew, so the container's paint did not follow the painter: $coverage",
    )
  }

  @Test
  fun `a success state plugin is given the decoded bitmap`() {
    val loader = warmLoader()
    var seen: ImageBitmap? = null
    val plugin = object : ImagePlugin.SuccessStatePlugin {
      @Composable
      override fun compose(
        modifier: Modifier,
        imageModel: Any?,
        imageOptions: ImageOptions,
        imageBitmap: ImageBitmap?,
      ): ImagePlugin = apply { seen = imageBitmap }
    }

    render(content = image(loader, component(plugin)))

    val bitmap = assertNotNull(seen, "the success state plugin never ran, or was handed no bitmap")
    assertEquals(imageSize, bitmap.width)
  }

  @Test
  fun `a composable plugin still wraps the content`() {
    val loader = warmLoader()
    var wrapped = false

    val plugin = object : ImagePlugin.ComposablePlugin {
      @Composable
      override fun compose(content: @Composable () -> Unit) {
        wrapped = true
        content()
      }
    }

    val pixels = render(content = image(loader, component(plugin)))

    assertTrue(wrapped, "the composable plugin never wrapped the content")
    assertTrue(pixels.coverage() > 0.9, "the wrapped content drew nothing")
  }

  @Test
  fun `the real zoomable plugin still draws the image`() {
    val loader = warmLoader()

    val pixels = render(content = image(loader, component(ZoomablePlugin())))

    assertTrue(pixels.coverage() > 0.9, "the zoomable plugin drew nothing")
  }

  @Test
  fun `a loading state plugin composes while the image is loading`() {
    val loader = hangingLoader()
    var composed = false
    val plugin = object : ImagePlugin.LoadingStatePlugin {
      @Composable
      override fun compose(
        modifier: Modifier,
        imageOptions: ImageOptions,
        executor: @Composable (IntSize) -> Unit,
      ): ImagePlugin = apply {
        composed = true
        Box(Modifier.fillMaxSize().background(Color.Green))
      }
    }

    val pixels = render(content = image(loader, component(plugin)))

    assertTrue(composed, "the loading state plugin never ran")
    assertEquals(0xFF00FF00.toInt(), pixels[pixels.size / 2], "the loading content was not drawn")
  }

  @Test
  fun `the real shimmer plugin composes while the image is loading`() {
    val loader = hangingLoader()

    val pixels = render(content = image(loader, component(ShimmerPlugin())))

    assertTrue(pixels.coverage() > 0.9, "the shimmer drew nothing while loading")
  }

  @Test
  fun `a caller success slot receives the painter and draws it`() {
    val loader = warmLoader()
    var reached = false
    val pixels = render(
      content = image(loader, component()) { _, painter ->
        reached = true
        Image(
          painter = painter,
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
        )
      },
    )

    assertTrue(reached, "the success slot never ran")
    assertTrue(pixels.coverage() > 0.9, "the success slot drew nothing")
  }

  /** Renders [frames] frames and returns how many times an ancestor of the image had to redraw. */
  private fun ancestorDraws(frames: Int, component: ImageComponent): Int {
    val loader = warmLoader()
    var draws = 0
    val scene = ImageComposeScene(
      width = sceneSize,
      height = sceneSize,
      density = Density(1f),
      coroutineContext = Dispatchers.Unconfined,
      content = {
        Box(Modifier.size(sceneSize.dp).drawBehind { draws++ }) {
          LandscapistImage(
            imageModel = { url },
            landscapist = loader,
            component = component,
            modifier = Modifier.size(sceneSize.dp),
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          )
        }
      },
    )
    try {
      repeat(frames) { frame -> scene.render(frame.toLong() * 16_000_000).close() }
    } finally {
      scene.close()
    }
    return draws
  }

  @Test
  fun `an animating painter does not repaint the tree around it more than a still one`() {
    // A painter that reads state as it draws has that read attributed to the nearest node owning
    // a layer, so the control has to be on the same path: a painter plugin that changes nothing.
    val stillPlugin = object : ImagePlugin.PainterPlugin {
      @Composable
      override fun compose(imageBitmap: ImageBitmap, painter: Painter): Painter = painter
    }
    val still = ancestorDraws(frames = 12, component = component(stillPlugin))
    val revealing = ancestorDraws(frames = 12, component = component(CircularRevealPlugin(200)))

    assertTrue(
      still <= 4,
      "the control redrew its ancestor $still times over 12 frames, so it has regressed too and " +
        "the comparison below means nothing",
    )
    assertTrue(
      revealing <= still,
      "the reveal cost $revealing ancestor redraws where a still image cost $still",
    )
  }

  /** Warms [urls] into one loader, each decoding to its own colour so a fade is visible. */
  private fun warmLoader(urls: List<String>): Landscapist {
    val colours = urls.withIndex().associate { (index, url) -> url to distinctColours[index] }
    val loader = Landscapist.builder().noDiskCache().fetcher(
      object : ImageFetcher {
        override fun canHandle(model: Any?): Boolean = true
        override suspend fun fetch(request: ImageRequest): FetchResult = FetchResult.Success(
          data = byteArrayOf(urls.indexOf(request.model as String).toByte()),
          mimeType = "image/png",
        )
      },
    ).decoder(
      object : ImageDecoder {
        override suspend fun decode(
          data: ByteArray,
          mimeType: String?,
          targetWidth: Int?,
          targetHeight: Int?,
          config: LandscapistConfig,
        ): DecodeResult {
          val colour = distinctColours[data[0].toInt()]
          val bitmap = ImageBitmap(imageSize, imageSize)
          val bounds = Rect(Offset.Zero, Size(imageSize.toFloat(), imageSize.toFloat()))
          Canvas(bitmap).drawRect(bounds, Paint().apply { color = colour })
          return DecodeResult.Success(bitmap, imageSize, imageSize)
        }
      },
    ).build()
    check(colours.size == urls.size)
    runBlocking {
      for (url in urls) {
        loader.load(
          ImageRequest.builder().model(url).diskCachePolicy(CachePolicy.DISABLED).build(),
        ).first { it is ImageResult.Success }
      }
    }
    return loader
  }

  @Test
  fun `a crossfade does not fade in an image that was already in memory`() {
    // An image read from the memory cache is already what the viewer is looking at.
    val loader = warmLoader()

    val pixels = render(content = image(loader, component(CrossfadePlugin(duration = 300))))

    // Full alpha, not merely non-zero: a frame part way through a fade covers the node too.
    val faintest = pixels.minOf { it ushr 24 }
    assertEquals(0xFF, faintest, "the first frame was faded rather than drawn")
  }

  /** Colours handed out to the models of a multi image test, in order. */
  private val distinctColours = listOf(Color.Red, Color.Blue, Color.Green)

  @Test
  fun `a crossfade fades in an image that replaces one already on screen`() {
    val second = "https://example.com/second.png"
    val loader = warmLoader(listOf(url, second))
    var model by mutableStateOf(url)
    val scene = ImageComposeScene(
      width = sceneSize,
      height = sceneSize,
      density = Density(1f),
      coroutineContext = Dispatchers.Unconfined,
      content = {
        LandscapistImage(
          imageModel = { model },
          landscapist = loader,
          component = component(CrossfadePlugin(duration = 300)),
          modifier = Modifier.size(sceneSize.dp),
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
        )
      },
    )
    val (before, colours) = try {
      scene.render(0L).close()
      val first = readCentre(scene.render(1L))
      model = second
      Snapshot.sendApplyNotifications()
      // The animation runs on the scene's clock, so it only advances when a frame is drawn.
      // Past the 300ms duration, so the last frame is the settled one.
      first to (1..16).map { frame -> readCentre(scene.render(frame * 40L * 1_000_000)) }
    } finally {
      scene.close()
    }
    val alphas = colours.map { it ushr 24 }

    assertEquals(0xFF, before ushr 24, "the image already on screen was not opaque")
    // The arriving image dissolves over the one underneath, so it never dips through transparent.
    assertTrue(
      alphas.all { it == 0xFF },
      "the image went transparent while the new one faded in, the alphas were $alphas",
    )
    // Only a dissolve produces a frame that is neither colour; opacity alone cannot show it.
    assertTrue(
      colours.any { it != pureRed && it != pureBlue },
      "no frame was part way between the two images, the colours were " +
        colours.joinToString { it.toUInt().toString(16) },
    )
    assertEquals(pureBlue, colours.last(), "the replacing image never arrived")
  }

  @Test
  fun `a crossfade still fades when the caller takes the painter with a success slot`() {
    // A success slot means the painter cannot be what fades, so the composable crossfade runs.
    val second = "https://example.com/second.png"
    val loader = warmLoader(listOf(url, second))
    var model by mutableStateOf(url)
    val scene = ImageComposeScene(
      width = sceneSize,
      height = sceneSize,
      density = Density(1f),
      coroutineContext = Dispatchers.Unconfined,
      content = {
        LandscapistImage(
          imageModel = { model },
          landscapist = loader,
          component = component(CrossfadePlugin(duration = 300)),
          modifier = Modifier.size(sceneSize.dp),
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          success = { _, painter ->
            Image(painter = painter, contentDescription = null, modifier = Modifier.fillMaxSize())
          },
        )
      },
    )
    val colours = try {
      scene.render(0L).close()
      scene.render(1L).close()
      model = second
      Snapshot.sendApplyNotifications()
      (1..16).map { frame -> readCentre(scene.render(frame * 40L * 1_000_000)) }
    } finally {
      scene.close()
    }

    assertTrue(
      colours.any { it != pureRed && it != pureBlue },
      "the image with a success slot did not fade, the colours were " +
        colours.joinToString { it.toUInt().toString(16) },
    )
    assertEquals(pureBlue, colours.last(), "the replacing image never arrived")
  }

  private val pureRed = 0xFFFF0000.toInt()
  private val pureBlue = 0xFF0000FF.toInt()

  /** The centre pixel of [image] as ARGB, closing it on the way out. */
  private fun readCentre(image: org.jetbrains.skia.Image): Int = try {
    val bytes = org.jetbrains.skia.Bitmap().use { bitmap ->
      bitmap.allocN32Pixels(image.width, image.height)
      check(image.readPixels(bitmap, 0, 0)) { "could not read the frame back" }
      bitmap.readPixels() ?: error("no pixels")
    }
    val offset = ((sceneSize / 2) * sceneSize + sceneSize / 2) * 4
    (bytes[offset + 3].toInt() and 0xFF shl 24) or
      (bytes[offset + 2].toInt() and 0xFF shl 16) or
      (bytes[offset + 1].toInt() and 0xFF shl 8) or
      (bytes[offset].toInt() and 0xFF)
  } finally {
    image.close()
  }
}
