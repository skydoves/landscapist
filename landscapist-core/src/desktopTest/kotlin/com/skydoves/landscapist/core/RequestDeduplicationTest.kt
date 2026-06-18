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

import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.ImageDecoder
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies in-flight request coalescing: concurrent loads that share a memory-cache key are fetched
 * and decoded once, while distinct models are not coalesced.
 */
class RequestDeduplicationTest {

  private class CountingFetcher(private val delayMs: Long) : ImageFetcher {
    val count = atomic(0)
    override suspend fun fetch(request: ImageRequest): FetchResult {
      count.incrementAndGet()
      delay(delayMs)
      return FetchResult.Success(data = byteArrayOf(1, 2, 3, 4), mimeType = "image/png")
    }

    override fun canHandle(model: Any?): Boolean = true
  }

  private class CountingDecoder : ImageDecoder {
    val count = atomic(0)
    override suspend fun decode(
      data: ByteArray,
      mimeType: String?,
      targetWidth: Int?,
      targetHeight: Int?,
      config: LandscapistConfig,
    ): DecodeResult {
      count.incrementAndGet()
      return DecodeResult.Success(bitmap = "decoded", width = 8, height = 8)
    }
  }

  private fun newLoader(fetcher: ImageFetcher, decoder: ImageDecoder): Landscapist =
    Landscapist.builder().fetcher(fetcher).decoder(decoder).build()

  private suspend fun Landscapist.awaitTerminal(model: String): ImageResult {
    val request = ImageRequest.builder()
      .model(model)
      .diskCachePolicy(CachePolicy.DISABLED)
      .build()
    return load(request).first { it is ImageResult.Success || it is ImageResult.Failure }
  }

  @Test
  fun concurrentIdenticalLoadsAreCoalescedIntoOneFetchAndDecode() = runBlocking {
    val fetcher = CountingFetcher(delayMs = 300)
    val decoder = CountingDecoder()
    val loader = newLoader(fetcher, decoder)

    val results = coroutineScope {
      (1..12).map {
        async(Dispatchers.Default) { loader.awaitTerminal("https://example.com/coalesce.png") }
      }.awaitAll()
    }

    assertTrue(results.all { it is ImageResult.Success }, "all concurrent loads should succeed")
    assertEquals(1, fetcher.count.value, "12 concurrent identical loads should fetch once")
    assertEquals(1, decoder.count.value, "12 concurrent identical loads should decode once")
  }

  @Test
  fun distinctModelsAreNotCoalesced() = runBlocking {
    val fetcher = CountingFetcher(delayMs = 100)
    val decoder = CountingDecoder()
    val loader = newLoader(fetcher, decoder)

    val results = coroutineScope {
      (1..5).map { index ->
        async(Dispatchers.Default) { loader.awaitTerminal("https://example.com/img_$index.png") }
      }.awaitAll()
    }

    assertTrue(results.all { it is ImageResult.Success })
    assertEquals(5, fetcher.count.value, "5 distinct models should fetch five times")
  }

  @Test
  fun sequentialLoadAfterCompletionFetchesAgainWhenNotCached() = runBlocking {
    val fetcher = CountingFetcher(delayMs = 10)
    val decoder = CountingDecoder()
    val loader = newLoader(fetcher, decoder)

    // Memory cache disabled so the second load cannot be served from cache and the coalescing entry
    // is already gone, proving the in-flight map is cleaned up after completion.
    val request = ImageRequest.builder()
      .model("https://example.com/sequential.png")
      .diskCachePolicy(CachePolicy.DISABLED)
      .memoryCachePolicy(CachePolicy.DISABLED)
      .build()

    loader.load(request).first { it is ImageResult.Success }
    loader.load(request).first { it is ImageResult.Success }

    assertEquals(2, fetcher.count.value, "two sequential uncached loads should fetch twice")
  }

  @Test
  fun siblingCompletesWhenAnotherCallerCancels() = runBlocking {
    val fetcher = CountingFetcher(delayMs = 300)
    val decoder = CountingDecoder()
    val loader = newLoader(fetcher, decoder)
    val model = "https://example.com/sibling.png"

    val survivorResult = coroutineScope {
      val leaving = launch(Dispatchers.Default) { loader.awaitTerminal(model) }
      val survivor = async(Dispatchers.Default) { loader.awaitTerminal(model) }
      delay(100) // both have joined the in-flight load while the fetch is still running
      leaving.cancel()
      survivor.await()
    }

    assertTrue(survivorResult is ImageResult.Success, "the surviving caller should still succeed")
    assertEquals(1, fetcher.count.value, "one fetch despite a sibling cancelling")
    assertEquals(1, decoder.count.value)
  }

  @Test
  fun soleCallerCancellationStopsTheSharedWork() = runBlocking {
    val fetcher = CountingFetcher(delayMs = 400)
    val decoder = CountingDecoder()
    val loader = newLoader(fetcher, decoder)
    val model = "https://example.com/abandon.png"

    val job = launch(Dispatchers.Default) { loader.awaitTerminal(model) }
    delay(120) // the fetch has started (count incremented before its delay)
    job.cancel()
    job.join()
    delay(150) // give any leaked work time to (wrongly) reach the decode step

    assertEquals(1, fetcher.count.value, "the fetch started once")
    assertEquals(0, decoder.count.value, "decode must not run after the sole caller cancels")
  }
}
