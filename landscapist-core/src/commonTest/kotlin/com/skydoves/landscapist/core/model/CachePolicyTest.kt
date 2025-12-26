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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CachePolicyTest {

  @Test
  fun `CachePolicy has four values`() {
    val values = CachePolicy.entries

    assertEquals(4, values.size)
  }

  @Test
  fun `ENABLED allows read and write`() {
    val policy = CachePolicy.ENABLED

    assertTrue(policy.readEnabled)
    assertTrue(policy.writeEnabled)
  }

  @Test
  fun `READ_ONLY allows read but not write`() {
    val policy = CachePolicy.READ_ONLY

    assertTrue(policy.readEnabled)
    assertFalse(policy.writeEnabled)
  }

  @Test
  fun `WRITE_ONLY allows write but not read`() {
    val policy = CachePolicy.WRITE_ONLY

    assertFalse(policy.readEnabled)
    assertTrue(policy.writeEnabled)
  }

  @Test
  fun `DISABLED disallows read and write`() {
    val policy = CachePolicy.DISABLED

    assertFalse(policy.readEnabled)
    assertFalse(policy.writeEnabled)
  }

  @Test
  fun `valueOf returns correct policy`() {
    assertEquals(CachePolicy.ENABLED, CachePolicy.valueOf("ENABLED"))
    assertEquals(CachePolicy.READ_ONLY, CachePolicy.valueOf("READ_ONLY"))
    assertEquals(CachePolicy.WRITE_ONLY, CachePolicy.valueOf("WRITE_ONLY"))
    assertEquals(CachePolicy.DISABLED, CachePolicy.valueOf("DISABLED"))
  }

  @Test
  fun `name returns correct string`() {
    assertEquals("ENABLED", CachePolicy.ENABLED.name)
    assertEquals("READ_ONLY", CachePolicy.READ_ONLY.name)
    assertEquals("WRITE_ONLY", CachePolicy.WRITE_ONLY.name)
    assertEquals("DISABLED", CachePolicy.DISABLED.name)
  }

  @Test
  fun `readEnabled matrix is correct`() {
    val readEnabledPolicies = CachePolicy.entries.filter { it.readEnabled }

    assertEquals(2, readEnabledPolicies.size)
    assertTrue(readEnabledPolicies.contains(CachePolicy.ENABLED))
    assertTrue(readEnabledPolicies.contains(CachePolicy.READ_ONLY))
  }

  @Test
  fun `writeEnabled matrix is correct`() {
    val writeEnabledPolicies = CachePolicy.entries.filter { it.writeEnabled }

    assertEquals(2, writeEnabledPolicies.size)
    assertTrue(writeEnabledPolicies.contains(CachePolicy.ENABLED))
    assertTrue(writeEnabledPolicies.contains(CachePolicy.WRITE_ONLY))
  }
}
