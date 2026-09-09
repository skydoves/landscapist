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
package com.skydoves.landscapist.core.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CacheKeyTest {

  @Test
  fun `diskKey generates consistent hash for same URL`() {
    val key1 = CacheKey(url = "https://example.com/image.jpg")
    val key2 = CacheKey(url = "https://example.com/image.jpg")

    assertEquals(key1.diskKey, key2.diskKey)
  }

  @Test
  fun `diskKey differs for different URLs`() {
    val key1 = CacheKey(url = "https://example.com/image1.jpg")
    val key2 = CacheKey(url = "https://example.com/image2.jpg")

    assertNotEquals(key1.diskKey, key2.diskKey)
  }

  @Test
  fun `diskKey ignores the target size`() {
    // The disk cache holds the downloaded bytes, and every size is decoded from those same bytes.
    val plain = CacheKey(url = "https://example.com/image.jpg")
    val sized = CacheKey(url = "https://example.com/image.jpg", width = 100, height = 200)
    val otherSize = CacheKey(url = "https://example.com/image.jpg", width = 400, height = 800)

    assertEquals(plain.diskKey, sized.diskKey)
    assertEquals(plain.diskKey, otherSize.diskKey)
  }

  @Test
  fun `diskKey ignores transformations`() {
    // Transformations run on the decoded image, so they change nothing about the bytes on disk.
    val plain = CacheKey(url = "https://example.com/image.jpg")
    val transformed = CacheKey(
      url = "https://example.com/image.jpg",
      transformationKeys = listOf("blur", "grayscale"),
    )

    assertEquals(plain.diskKey, transformed.diskKey)
  }

  @Test
  fun `diskKey still separates different images`() {
    val one = CacheKey(url = "https://example.com/one.jpg")
    val two = CacheKey(url = "https://example.com/two.jpg")

    assertNotEquals(one.diskKey, two.diskKey)
  }

  @Test
  fun `memoryKey contains URL`() {
    val key = CacheKey(url = "https://example.com/image.jpg")

    assertTrue(key.memoryKey.contains("https://example.com/image.jpg"))
  }

  @Test
  fun `memoryKey includes size when dimensions provided`() {
    val key = CacheKey(
      url = "https://example.com/image.jpg",
      width = 100,
      height = 200,
    )

    assertTrue(key.memoryKey.contains("100x200"))
  }

  @Test
  fun `memoryKey includes transformation keys`() {
    val key = CacheKey(
      url = "https://example.com/image.jpg",
      transformationKeys = listOf("blur", "grayscale"),
    )

    assertTrue(key.memoryKey.contains("blur"))
    assertTrue(key.memoryKey.contains("grayscale"))
  }

  @Test
  fun `create factory method handles String model`() {
    val key = CacheKey.create(model = "https://example.com/image.jpg")

    assertEquals("https://example.com/image.jpg", key.url)
  }

  @Test
  fun `create factory method handles null model`() {
    val key = CacheKey.create(model = null)

    assertEquals("", key.url)
  }

  @Test
  fun `create factory method handles custom object model`() {
    data class CustomModel(val id: Int)
    val model = CustomModel(id = 123)
    val key = CacheKey.create(model = model)

    assertEquals(model.toString(), key.url)
  }

  @Test
  fun `create factory method passes transformations`() {
    val key = CacheKey.create(
      model = "https://example.com/image.jpg",
      transformationKeys = listOf("rotate"),
    )

    assertEquals(listOf("rotate"), key.transformationKeys)
  }

  @Test
  fun `create factory method passes dimensions`() {
    val key = CacheKey.create(
      model = "https://example.com/image.jpg",
      width = 50,
      height = 100,
    )

    assertEquals(50, key.width)
    assertEquals(100, key.height)
  }

  @Test
  fun `same keys are equal`() {
    val key1 = CacheKey(
      url = "https://example.com/image.jpg",
      transformationKeys = listOf("blur"),
      width = 100,
      height = 200,
    )
    val key2 = CacheKey(
      url = "https://example.com/image.jpg",
      transformationKeys = listOf("blur"),
      width = 100,
      height = 200,
    )

    assertEquals(key1, key2)
    assertEquals(key1.hashCode(), key2.hashCode())
  }

  @Test
  fun `different keys are not equal`() {
    val key1 = CacheKey(url = "https://example.com/image1.jpg")
    val key2 = CacheKey(url = "https://example.com/image2.jpg")

    assertNotEquals(key1, key2)
  }
}
