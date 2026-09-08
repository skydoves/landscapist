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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LocalLandscapist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import coil3.request.ImageRequest as CoilImageRequest

/**
 * The Compose layer, measured the way a user experiences it: how long from entering composition to
 * a frame that actually has the image in it.
 *
 * Rendering happens through [ImageComposeScene], which composes, lays out and rasterizes offscreen,
 * so no window or display is needed. Both libraries are given a warm memory cache first, because
 * that is the state a list is in once it has been scrolled through, and it is the state where a
 * loader either draws immediately or shows a frame of nothing.
 */
internal fun composeComparison() {
  val landscapistCounter = FetchCounter()
  val coilCounter = FetchCounter()
  val landscapist = newLandscapist(landscapistCounter)
  val coil = newCoil(coilCounter)
  val models = List(ITEM_COUNT) { "https://example.com/list-item-$it.jpg" }

  // Warm both caches, which is what a second pass over a list sees.
  runBlocking {
    for (model in models) {
      landscapist.load(landscapistRequest(model, ITEM_SIZE)).first { it is ImageResult.Success }
      coil.execute(coilRequest(model, ITEM_SIZE))
    }
  }

  val warmups = 20
  val iterations = 60

  val landscapistFrames = measure("landscapist", warmups, iterations) {
    renderOnce { LandscapistList(landscapist, models) }
  }
  settle()
  val coilFrames = measure("coil", warmups, iterations) {
    renderOnce { CoilList(coil, models) }
  }

  report("first frame, $ITEM_COUNT cached images", landscapistFrames, coilFrames)

  val landscapistBytes = allocatedBytes {
    repeat(iterations) { renderOnce { LandscapistList(landscapist, models) } }
  }
  val coilBytes = allocatedBytes {
    repeat(iterations) { renderOnce { CoilList(coil, models) } }
  }
  // The scene itself composes, lays out and rasterizes, so measure that floor and subtract it.
  val emptyBytes = allocatedBytes {
    repeat(iterations) { renderOnce { EmptyList() } }
  }
  val floor = emptyBytes / iterations
  // Where the bytes go: the framework around the image versus drawing the image itself.
  val shellBytes = allocatedBytes {
    repeat(iterations) { renderOnce { LandscapistShellList(landscapist, models) } }
  }
  println("allocation per first frame of $ITEM_COUNT images")
  println("  empty scene    ${floor.formatBytes()}  (the floor, subtracted below)")
  println("  landscapist    ${(landscapistBytes / iterations - floor).formatBytes()}")
  println("  coil           ${(coilBytes / iterations - floor).formatBytes()}")
  val shell = (shellBytes / iterations - floor).formatBytes()
  println("    landscapist with an empty success slot (framework only): $shell")
  println()
}

private const val ITEM_COUNT = 20
private const val ITEM_SIZE = 128

/**
 * Composes, lays out and rasterizes one frame.
 *
 * The scene is created and closed per measurement on purpose: a composable that resolves its image
 * on first composition is exactly what is being measured, and reusing a scene would hide it.
 */
private inline fun renderOnce(crossinline content: @Composable () -> Unit) {
  val scene = ImageComposeScene(
    width = ITEM_SIZE,
    height = ITEM_SIZE * ITEM_COUNT,
    density = Density(1f),
    coroutineContext = Dispatchers.Unconfined,
    content = { content() },
  )
  try {
    scene.render(0L).close()
  } finally {
    scene.close()
  }
}

@Composable
private fun EmptyList() {
  Column {
    repeat(ITEM_COUNT) {
      Box(Modifier.size(ITEM_SIZE.dp))
    }
  }
}

@Composable
private fun LandscapistList(
  landscapist: Landscapist,
  models: List<String>,
) {
  CompositionLocalProvider(LocalLandscapist provides landscapist) {
    Column {
      for (model in models) {
        LandscapistImage(
          imageModel = { model },
          landscapist = landscapist,
          modifier = Modifier.size(ITEM_SIZE.dp),
        )
      }
    }
  }
}

/** The same composable with nothing drawn, separating framework cost from drawing cost. */
@Composable
private fun LandscapistShellList(
  landscapist: Landscapist,
  models: List<String>,
) {
  Column {
    for (model in models) {
      LandscapistImage(
        imageModel = { model },
        landscapist = landscapist,
        modifier = Modifier.size(ITEM_SIZE.dp),
        success = { _, _ -> },
      )
    }
  }
}

@Composable
private fun CoilList(imageLoader: ImageLoader, models: List<String>) {
  Column {
    for (model in models) {
      AsyncImage(
        model = CoilImageRequest.Builder(LocalPlatformContext.current)
          .data(model)
          .size(ITEM_SIZE, ITEM_SIZE)
          .build(),
        contentDescription = null,
        imageLoader = imageLoader,
        modifier = Modifier.size(ITEM_SIZE.dp),
      )
    }
  }
}
