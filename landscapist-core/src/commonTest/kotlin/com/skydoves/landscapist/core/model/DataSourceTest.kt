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
package com.skydoves.landscapist.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataSourceTest {

  @Test
  fun `DataSource has four values`() {
    val values = DataSource.entries

    assertEquals(4, values.size)
  }

  @Test
  fun `DataSource contains MEMORY`() {
    assertTrue(DataSource.entries.contains(DataSource.MEMORY))
  }

  @Test
  fun `DataSource contains DISK`() {
    assertTrue(DataSource.entries.contains(DataSource.DISK))
  }

  @Test
  fun `DataSource contains NETWORK`() {
    assertTrue(DataSource.entries.contains(DataSource.NETWORK))
  }

  @Test
  fun `DataSource contains UNKNOWN`() {
    assertTrue(DataSource.entries.contains(DataSource.UNKNOWN))
  }

  @Test
  fun `DataSource valueOf returns correct value`() {
    assertEquals(DataSource.MEMORY, DataSource.valueOf("MEMORY"))
    assertEquals(DataSource.DISK, DataSource.valueOf("DISK"))
    assertEquals(DataSource.NETWORK, DataSource.valueOf("NETWORK"))
    assertEquals(DataSource.UNKNOWN, DataSource.valueOf("UNKNOWN"))
  }

  @Test
  fun `DataSource name returns correct string`() {
    assertEquals("MEMORY", DataSource.MEMORY.name)
    assertEquals("DISK", DataSource.DISK.name)
    assertEquals("NETWORK", DataSource.NETWORK.name)
    assertEquals("UNKNOWN", DataSource.UNKNOWN.name)
  }

  @Test
  fun `DataSource ordinal values are sequential`() {
    val values = DataSource.entries
    values.forEachIndexed { index, dataSource ->
      assertEquals(index, dataSource.ordinal)
    }
  }
}
