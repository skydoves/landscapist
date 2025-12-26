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
package com.skydoves.landscapist.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownsamplingStrategyTest {

  @Test
  fun `DownsamplingStrategy has four values`() {
    val values = DownsamplingStrategy.entries

    assertEquals(4, values.size)
  }

  @Test
  fun `DownsamplingStrategy contains AUTO`() {
    assertTrue(DownsamplingStrategy.entries.contains(DownsamplingStrategy.AUTO))
  }

  @Test
  fun `DownsamplingStrategy contains NONE`() {
    assertTrue(DownsamplingStrategy.entries.contains(DownsamplingStrategy.NONE))
  }

  @Test
  fun `DownsamplingStrategy contains FIT`() {
    assertTrue(DownsamplingStrategy.entries.contains(DownsamplingStrategy.FIT))
  }

  @Test
  fun `DownsamplingStrategy contains FILL`() {
    assertTrue(DownsamplingStrategy.entries.contains(DownsamplingStrategy.FILL))
  }

  @Test
  fun `valueOf returns correct strategy`() {
    assertEquals(DownsamplingStrategy.AUTO, DownsamplingStrategy.valueOf("AUTO"))
    assertEquals(DownsamplingStrategy.NONE, DownsamplingStrategy.valueOf("NONE"))
    assertEquals(DownsamplingStrategy.FIT, DownsamplingStrategy.valueOf("FIT"))
    assertEquals(DownsamplingStrategy.FILL, DownsamplingStrategy.valueOf("FILL"))
  }

  @Test
  fun `name returns correct string`() {
    assertEquals("AUTO", DownsamplingStrategy.AUTO.name)
    assertEquals("NONE", DownsamplingStrategy.NONE.name)
    assertEquals("FIT", DownsamplingStrategy.FIT.name)
    assertEquals("FILL", DownsamplingStrategy.FILL.name)
  }

  @Test
  fun `AUTO is first in enum`() {
    assertEquals(0, DownsamplingStrategy.AUTO.ordinal)
  }

  @Test
  fun `BitmapConfig uses strategy correctly`() {
    val autoConfig = BitmapConfig(strategy = DownsamplingStrategy.AUTO)
    val fitConfig = BitmapConfig(strategy = DownsamplingStrategy.FIT)
    val fillConfig = BitmapConfig(strategy = DownsamplingStrategy.FILL)
    val noneConfig = BitmapConfig(strategy = DownsamplingStrategy.NONE)

    assertEquals(DownsamplingStrategy.AUTO, autoConfig.strategy)
    assertEquals(DownsamplingStrategy.FIT, fitConfig.strategy)
    assertEquals(DownsamplingStrategy.FILL, fillConfig.strategy)
    assertEquals(DownsamplingStrategy.NONE, noneConfig.strategy)
  }
}
