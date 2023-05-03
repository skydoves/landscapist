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
package com.skydoves.landscapist.glide

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.bumptech.glide.Glide
import com.skydoves.landscapist.ImageOptions

/**
 * Remember the given [FlowCustomTarget] in the current composition.
 *
 * The [FlowCustomTarget] will be cleared when the object leaves the composition.
 *
 * @param target the [FlowCustomTarget] to be remembered
 * @param imageOptions the [ImageOptions] to be used a key.
 */
@Composable
internal fun rememberTarget(
  target: FlowCustomTarget,
  imageOptions: ImageOptions,
): FlowCustomTarget {
  val context = LocalContext.current
  return remember(target, imageOptions) { RememberableTarget(context, target) }.value
}

internal class RememberableTarget(
  private val context: Context,
  private val target: FlowCustomTarget,
) : RememberObserver {

  internal val value: FlowCustomTarget
    get() = target

  override fun onRemembered() {
    // no-op
  }

  override fun onAbandoned() {
    Glide.with(context).clear(target)
  }

  override fun onForgotten() {
    Glide.with(context).clear(target)
  }
}
