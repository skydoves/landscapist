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
package com.skydoves.landscapist.coil3

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.network.NetworkFetcher
import coil3.request.SuccessResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, DelicateCoilApi::class)
class LocalCoilProviderTest {

  @BeforeTest
  fun setUp() {
    SingletonImageLoader.reset()
  }

  @AfterTest
  fun tearDown() {
    SingletonImageLoader.reset()
  }

  @Test
  fun preservesAppRegisteredSingletonImageLoaderFactory() = runComposeUiTest {
    var factoryInvocations = 0
    var interceptorCalled = false
    var appImageLoader: ImageLoader? = null

    val interceptor = Interceptor { chain ->
      interceptorCalled = true
      SuccessResult(
        image = ColorImage(color = 0xFF0000FF.toInt(), width = 16, height = 16),
        request = chain.request,
        dataSource = DataSource.MEMORY,
      )
    }

    SingletonImageLoader.setSafe { context ->
      factoryInvocations++
      ImageLoader.Builder(context)
        .components {
          add(interceptor)
        }
        .build()
        .also { appImageLoader = it }
    }

    var resolvedLoader: ImageLoader? = null
    setContent {
      resolvedLoader = LocalCoilProvider.getCoilImageLoader()
      CoilImage(
        imageModel = { "https://example.com/image.png" },
        modifier = Modifier.size(32.dp),
      )
    }

    waitUntil(timeoutMillis = 5_000) { interceptorCalled }
    assertEquals(1, factoryInvocations)
    assertSame(appImageLoader, resolvedLoader)
  }

  @Test
  fun defaultSingletonImageLoaderIncludesNetworkFetcher() = runComposeUiTest {
    var resolvedLoader: ImageLoader? = null
    setContent {
      resolvedLoader = LocalCoilProvider.getCoilImageLoader()
    }
    waitForIdle()

    val loader = requireNotNull(resolvedLoader)
    assertSame(SingletonImageLoader.get(PlatformContext.INSTANCE), loader)
    assertTrue(
      loader.components.fetcherFactories.any { (factory, _) -> factory is NetworkFetcher.Factory },
    )
  }

  @Test
  fun prefersLocalCoilImageLoaderOverSingletonFactory() = runComposeUiTest {
    var factoryInvoked = false
    SingletonImageLoader.setSafe { context ->
      factoryInvoked = true
      ImageLoader.Builder(context).build()
    }
    val explicitLoader = ImageLoader.Builder(PlatformContext.INSTANCE).build()

    var resolvedLoader: ImageLoader? = null
    setContent {
      CompositionLocalProvider(LocalCoilImageLoader provides explicitLoader) {
        resolvedLoader = LocalCoilProvider.getCoilImageLoader()
      }
    }
    waitForIdle()

    assertSame(explicitLoader, resolvedLoader)
    assertFalse(factoryInvoked)
  }
}
