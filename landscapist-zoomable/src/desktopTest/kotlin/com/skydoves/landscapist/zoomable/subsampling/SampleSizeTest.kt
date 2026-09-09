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
package com.skydoves.landscapist.zoomable.subsampling

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sample size a tile grid asks the decoder for.
 *
 * It used to be found by doubling a counter until it no longer fit, with nothing bounding either
 * end. A viewport measured to nothing divides to infinity, so the counter climbed to 2^30,
 * overflowed to `Int.MIN_VALUE`, went to zero, and stayed there with the loop still running: a
 * sub-sampled image measured to zero on one axis froze the thread it was laid out on.
 */
class SampleSizeTest {

  @Test
  fun `a zoom of nothing does not hang`() {
    // Ten milliseconds is generous for arithmetic; the old loop never returned at all.
    val started = System.nanoTime()
    val sample = TileGrid.calculateSampleSizeForZoom(0f)
    val elapsedMs = (System.nanoTime() - started) / 1_000_000

    assertTrue(elapsedMs < 1_000, "it took ${elapsedMs}ms, which is a loop that does not end")
    assertTrue(sample >= 1, "a sample size below one asks the decoder for nothing: $sample")
  }

  @Test
  fun `a zoom that is not a number does not hang`() {
    assertEquals(1, TileGrid.calculateSampleSizeForZoom(Float.NaN))
  }

  @Test
  fun `zooming in never samples away detail`() {
    for (zoom in listOf(1f, 1.5f, 2f, 8f, 40f)) {
      assertEquals(1, TileGrid.calculateSampleSizeForZoom(zoom), "wrong at $zoom")
    }
  }

  @Test
  fun `zooming out halves`() {
    assertEquals(2, TileGrid.calculateSampleSizeForZoom(0.5f))
    assertEquals(4, TileGrid.calculateSampleSizeForZoom(0.25f))
    assertEquals(8, TileGrid.calculateSampleSizeForZoom(0.125f))
  }
}
