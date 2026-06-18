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
package com.skydoves.benchmark.landscapist.app

/**
 * Distinct, cacheable image URLs for the scrolling benchmark lists.
 *
 * Each list row uses a unique image so scrolling triggers real fetch + decode work for every
 * library, rather than repeatedly drawing one cached bitmap. The `size` segment requests a
 * server-resized image so decode cost tracks the on-screen item size. Swap [url] to benchmark
 * against your own CDN.
 */
internal object BenchmarkImages {

  /** Number of images per library list. Large enough that the list scrolls past several screens. */
  private const val COUNT = 30

  /** Height of each list item, in dp. */
  const val ITEM_HEIGHT_DP = 200

  fun urls(size: Int = 400): List<String> = List(COUNT) { index -> url(index, size) }

  private fun url(seed: Int, size: Int): String =
    "https://picsum.photos/seed/landscapist_$seed/$size/$size"
}
