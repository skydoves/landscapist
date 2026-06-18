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
package com.skydoves.landscapist.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.builder

// Process-wide default Landscapist for Android. Building one per composition subtree would give each
// its own memory cache, disk cache, scheduler, and in-flight request map, fragmenting the cache hit
// rate and the request coalescing. A single shared instance matches the Skia targets, which already
// fall back to Landscapist.getInstance().
private val androidDefaultLock = Any()

@Volatile
private var androidDefaultLandscapist: Landscapist? = null

/**
 * Android implementation that resolves the [Landscapist] instance.
 *
 * An explicit [LocalLandscapist] provider takes precedence; otherwise a single process-wide
 * instance (configured with the application context for disk caching) is shared across the app. On
 * Android the override mechanism is [LocalLandscapist]; `Landscapist.setInstance` does not change
 * this default.
 */
@Composable
public actual fun getLandscapist(): Landscapist {
  LocalLandscapist.current?.let { return it }

  val context = LocalContext.current.applicationContext
  return remember {
    androidDefaultLandscapist ?: synchronized(androidDefaultLock) {
      androidDefaultLandscapist ?: Landscapist.builder(context).build().also {
        androidDefaultLandscapist = it
      }
    }
  }
}
