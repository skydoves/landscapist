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
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageResultTest {

  @Test
  fun `Loading is a singleton`() {
    val loading1 = ImageResult.Loading
    val loading2 = ImageResult.Loading

    assertEquals(loading1, loading2)
  }

  @Test
  fun `Success stores all properties`() {
    val rawData = byteArrayOf(1, 2, 3)
    val success = ImageResult.Success(
      data = "bitmap",
      dataSource = DataSource.NETWORK,
      originalWidth = 100,
      originalHeight = 200,
      rawData = rawData,
      diskCachePath = "/cache/image.jpg",
      isIntermediate = false,
      progress = 1.0f,
    )

    assertEquals("bitmap", success.data)
    assertEquals(DataSource.NETWORK, success.dataSource)
    assertEquals(100, success.originalWidth)
    assertEquals(200, success.originalHeight)
    assertTrue(rawData.contentEquals(success.rawData!!))
    assertEquals("/cache/image.jpg", success.diskCachePath)
    assertFalse(success.isIntermediate)
    assertEquals(1.0f, success.progress)
  }

  @Test
  fun `Success isFinal returns true when not intermediate`() {
    val success = ImageResult.Success(
      data = "bitmap",
      dataSource = DataSource.MEMORY,
      isIntermediate = false,
    )

    assertTrue(success.isFinal)
  }

  @Test
  fun `Success isFinal returns false when intermediate`() {
    val success = ImageResult.Success(
      data = "bitmap",
      dataSource = DataSource.NETWORK,
      isIntermediate = true,
      progress = 0.5f,
    )

    assertFalse(success.isFinal)
  }

  @Test
  fun `Success default values`() {
    val success = ImageResult.Success(
      data = "bitmap",
      dataSource = DataSource.MEMORY,
    )

    assertEquals(0, success.originalWidth)
    assertEquals(0, success.originalHeight)
    assertNull(success.rawData)
    assertNull(success.diskCachePath)
    assertFalse(success.isIntermediate)
    assertEquals(1f, success.progress)
    assertTrue(success.isFinal)
  }

  @Test
  fun `Success equals with same rawData`() {
    val rawData = byteArrayOf(1, 2, 3)
    val success1 = ImageResult.Success(
      data = "bitmap",
      dataSource = DataSource.DISK,
      rawData = rawData,
    )
    val success2 = ImageResult.Success(
      data = "bitmap",
      dataSource = DataSource.DISK,
      rawData = byteArrayOf(1, 2, 3),
    )

    assertEquals(success1, success2)
  }

  @Test
  fun `Success not equals with different rawData`() {
    val success1 = ImageResult.Success(
      data = "bitmap",
      dataSource = DataSource.DISK,
      rawData = byteArrayOf(1, 2, 3),
    )
    val success2 = ImageResult.Success(
      data = "bitmap",
      dataSource = DataSource.DISK,
      rawData = byteArrayOf(4, 5, 6),
    )

    assertNotEquals(success1, success2)
  }

  @Test
  fun `Success equals with null rawData`() {
    val success1 = ImageResult.Success(
      data = "bitmap",
      dataSource = DataSource.DISK,
      rawData = null,
    )
    val success2 = ImageResult.Success(
      data = "bitmap",
      dataSource = DataSource.DISK,
      rawData = null,
    )

    assertEquals(success1, success2)
  }

  @Test
  fun `Success not equals when one has null rawData`() {
    val success1 = ImageResult.Success(
      data = "bitmap",
      dataSource = DataSource.DISK,
      rawData = byteArrayOf(1, 2, 3),
    )
    val success2 = ImageResult.Success(
      data = "bitmap",
      dataSource = DataSource.DISK,
      rawData = null,
    )

    assertNotEquals(success1, success2)
  }

  @Test
  fun `Success hashCode is consistent with equals`() {
    val rawData = byteArrayOf(1, 2, 3)
    val success1 = ImageResult.Success(
      data = "bitmap",
      dataSource = DataSource.NETWORK,
      originalWidth = 100,
      originalHeight = 200,
      rawData = rawData,
    )
    val success2 = ImageResult.Success(
      data = "bitmap",
      dataSource = DataSource.NETWORK,
      originalWidth = 100,
      originalHeight = 200,
      rawData = byteArrayOf(1, 2, 3),
    )

    assertEquals(success1.hashCode(), success2.hashCode())
  }

  @Test
  fun `Failure stores throwable and message`() {
    val error = RuntimeException("Network error")
    val failure = ImageResult.Failure(
      throwable = error,
      message = "Failed to load image",
    )

    assertEquals(error, failure.throwable)
    assertEquals("Failed to load image", failure.message)
  }

  @Test
  fun `Failure can have null throwable`() {
    val failure = ImageResult.Failure(
      throwable = null,
      message = "Unknown error",
    )

    assertNull(failure.throwable)
    assertEquals("Unknown error", failure.message)
  }

  @Test
  fun `Failure can have null message`() {
    val error = RuntimeException("Error")
    val failure = ImageResult.Failure(
      throwable = error,
      message = null,
    )

    assertEquals(error, failure.throwable)
    assertNull(failure.message)
  }

  @Test
  fun `Failure default values are null`() {
    val failure = ImageResult.Failure()

    assertNull(failure.throwable)
    assertNull(failure.message)
  }

  @Test
  fun `sealed class subtypes are correct`() {
    val loading: ImageResult = ImageResult.Loading
    val success: ImageResult = ImageResult.Success("data", DataSource.MEMORY)
    val failure: ImageResult = ImageResult.Failure()

    assertIs<ImageResult.Loading>(loading)
    assertIs<ImageResult.Success>(success)
    assertIs<ImageResult.Failure>(failure)
  }

  @Test
  fun `when expression covers all cases`() {
    val results = listOf(
      ImageResult.Loading,
      ImageResult.Success("data", DataSource.MEMORY),
      ImageResult.Failure(),
    )

    results.forEach { result ->
      val description = when (result) {
        is ImageResult.Loading -> "loading"
        is ImageResult.Success -> "success"
        is ImageResult.Failure -> "failure"
      }
      assertTrue(description.isNotEmpty())
    }
  }
}
