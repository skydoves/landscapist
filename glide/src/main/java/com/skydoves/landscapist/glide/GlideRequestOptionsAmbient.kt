/*
 * Designed and developed by 2020 skydoves (Jaewoong Eum)
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

@file:JvmName("GlideRequestOptionsAmbient")
@file:JvmMultifileClass
@file:Suppress("unused")

package com.skydoves.landscapist.glide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticAmbientOf
import com.bumptech.glide.request.RequestOptions

/**
 * Ambient containing the preferred [RequestOptions] for providing the same instance
 * in our composable hierarchy.
 */
val GlideRequestOptionsAmbient = staticAmbientOf<RequestOptions?> { null }

/** A provider for taking the ambient instances related to the `GlideImage`. */
object GlideAmbientProvider {

  /** Returns the current or default [RequestOptions] for the `GlideImage` parameter. */
  @Composable
  fun getGlideRequestOptions(): RequestOptions {
    return GlideRequestOptionsAmbient.current ?: RequestOptions()
  }
}
