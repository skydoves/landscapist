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

import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.scheduler.DecodePriority
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageRequestBuilderTest {

  @Test
  fun `builder creates request with default values`() {
    val request = ImageRequest.builder().build()

    assertNull(request.model)
    assertEquals(CachePolicy.ENABLED, request.memoryCachePolicy)
    assertEquals(CachePolicy.ENABLED, request.diskCachePolicy)
    assertTrue(request.headers.isEmpty())
    assertTrue(request.transformations.isEmpty())
    assertNull(request.targetWidth)
    assertNull(request.targetHeight)
    assertNull(request.tag)
    assertEquals(DecodePriority.NORMAL, request.priority)
    assertTrue(request.progressiveEnabled)
  }

  @Test
  fun `builder sets model`() {
    val url = "https://example.com/image.jpg"
    val request = ImageRequest.builder()
      .model(url)
      .build()

    assertEquals(url, request.model)
  }

  @Test
  fun `builder sets memory cache policy`() {
    val request = ImageRequest.builder()
      .memoryCachePolicy(CachePolicy.DISABLED)
      .build()

    assertEquals(CachePolicy.DISABLED, request.memoryCachePolicy)
  }

  @Test
  fun `builder sets disk cache policy`() {
    val request = ImageRequest.builder()
      .diskCachePolicy(CachePolicy.READ_ONLY)
      .build()

    assertEquals(CachePolicy.READ_ONLY, request.diskCachePolicy)
  }

  @Test
  fun `builder adds single header`() {
    val request = ImageRequest.builder()
      .addHeader("Authorization", "Bearer token123")
      .build()

    assertEquals("Bearer token123", request.headers["Authorization"])
    assertEquals(1, request.headers.size)
  }

  @Test
  fun `builder adds multiple headers`() {
    val request = ImageRequest.builder()
      .addHeader("Authorization", "Bearer token123")
      .addHeader("Accept", "image/*")
      .build()

    assertEquals("Bearer token123", request.headers["Authorization"])
    assertEquals("image/*", request.headers["Accept"])
    assertEquals(2, request.headers.size)
  }

  @Test
  fun `builder sets all headers replacing previous`() {
    val request = ImageRequest.builder()
      .addHeader("OldHeader", "old_value")
      .headers(mapOf("NewHeader" to "new_value"))
      .build()

    assertNull(request.headers["OldHeader"])
    assertEquals("new_value", request.headers["NewHeader"])
    assertEquals(1, request.headers.size)
  }

  @Test
  fun `builder sets size`() {
    val request = ImageRequest.builder()
      .size(width = 100, height = 200)
      .build()

    assertEquals(100, request.targetWidth)
    assertEquals(200, request.targetHeight)
  }

  @Test
  fun `builder sets tag`() {
    val request = ImageRequest.builder()
      .tag("my-tag")
      .build()

    assertEquals("my-tag", request.tag)
  }

  @Test
  fun `builder sets priority`() {
    val request = ImageRequest.builder()
      .priority(DecodePriority.HIGH)
      .build()

    assertEquals(DecodePriority.HIGH, request.priority)
  }

  @Test
  fun `builder sets progressiveEnabled to false`() {
    val request = ImageRequest.builder()
      .progressiveEnabled(false)
      .build()

    assertFalse(request.progressiveEnabled)
  }

  @Test
  fun `builder chains all options`() {
    val url = "https://example.com/image.jpg"
    val request = ImageRequest.builder()
      .model(url)
      .memoryCachePolicy(CachePolicy.DISABLED)
      .diskCachePolicy(CachePolicy.READ_ONLY)
      .addHeader("Authorization", "Bearer token")
      .size(width = 300, height = 400)
      .tag("test-tag")
      .priority(DecodePriority.LOW)
      .progressiveEnabled(false)
      .build()

    assertEquals(url, request.model)
    assertEquals(CachePolicy.DISABLED, request.memoryCachePolicy)
    assertEquals(CachePolicy.READ_ONLY, request.diskCachePolicy)
    assertEquals("Bearer token", request.headers["Authorization"])
    assertEquals(300, request.targetWidth)
    assertEquals(400, request.targetHeight)
    assertEquals("test-tag", request.tag)
    assertEquals(DecodePriority.LOW, request.priority)
    assertFalse(request.progressiveEnabled)
  }

  @Test
  fun `ImageRequest companion creates builder`() {
    val builder = ImageRequest.builder()

    // Just verify it returns a builder that can build
    val request = builder.build()
    assertNull(request.model)
  }

  @Test
  fun `builder can set null tag`() {
    val request = ImageRequest.builder()
      .tag("initial-tag")
      .tag(null)
      .build()

    assertNull(request.tag)
  }

  @Test
  fun `builder headers are immutable in request`() {
    val mutableHeaders = mutableMapOf("Key" to "Value")
    val request = ImageRequest.builder()
      .headers(mutableHeaders)
      .build()

    // Modifying original map should not affect request
    mutableHeaders["NewKey"] = "NewValue"

    assertNull(request.headers["NewKey"])
    assertEquals(1, request.headers.size)
  }

  @Test
  fun `CachePolicy ENABLED allows read and write`() {
    val policy = CachePolicy.ENABLED

    assertTrue(policy.readEnabled)
    assertTrue(policy.writeEnabled)
  }

  @Test
  fun `CachePolicy DISABLED disallows read and write`() {
    val policy = CachePolicy.DISABLED

    assertFalse(policy.readEnabled)
    assertFalse(policy.writeEnabled)
  }

  @Test
  fun `CachePolicy READ_ONLY allows read but not write`() {
    val policy = CachePolicy.READ_ONLY

    assertTrue(policy.readEnabled)
    assertFalse(policy.writeEnabled)
  }

  @Test
  fun `CachePolicy WRITE_ONLY allows write but not read`() {
    val policy = CachePolicy.WRITE_ONLY

    assertFalse(policy.readEnabled)
    assertTrue(policy.writeEnabled)
  }

  @Test
  fun `DecodePriority has correct priority values`() {
    // Higher value means higher priority
    assertTrue(DecodePriority.LOW.value < DecodePriority.NORMAL.value)
    assertTrue(DecodePriority.NORMAL.value < DecodePriority.HIGH.value)
    assertTrue(DecodePriority.HIGH.value < DecodePriority.IMMEDIATE.value)
    assertTrue(DecodePriority.BACKGROUND.value < DecodePriority.LOW.value)
  }
}
