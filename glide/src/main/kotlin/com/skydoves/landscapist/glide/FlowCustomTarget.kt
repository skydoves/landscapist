/*
 * Designed and developed by 2020-2022 skydoves (Jaewoong Eum)
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

import android.graphics.drawable.Drawable
import androidx.compose.ui.unit.IntSize
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.skydoves.landscapist.ImageLoadState
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.trySendBlocking

/**
 * FlowCustomTarget is a [CustomTarget] for receiving Glide image results from network and handle states.
 */
internal class FlowCustomTarget constructor(
  requestSize: IntSize,
  private val producerScope: ProducerScope<ImageLoadState>
) : CustomTarget<Any>(
  requestSize.width.takeIf { it > 0 } ?: Target.SIZE_ORIGINAL,
  requestSize.height.takeIf { it > 0 } ?: Target.SIZE_ORIGINAL
) {

  private var failException: Throwable? = null

  /** onResourceReady will be handled by [FlowRequestListener]. */
  override fun onResourceReady(resource: Any, transition: Transition<in Any>?) = Unit

  override fun onLoadStarted(placeholder: Drawable?) {
    super.onLoadStarted(placeholder)
    producerScope.trySendBlocking(ImageLoadState.Loading)
  }

  override fun onLoadFailed(errorDrawable: Drawable?) {
    super.onLoadFailed(errorDrawable)
    producerScope.trySendBlocking(
      ImageLoadState.Failure(
        data = errorDrawable,
        reason = failException
      )
    )
    producerScope.channel.close()
  }

  override fun onLoadCleared(placeholder: Drawable?) {
    // Glide wants to free up the resource, so we need to clear
    // the result, otherwise we might draw a recycled bitmap later.
    producerScope.trySendBlocking(ImageLoadState.None)
    producerScope.channel.close()
  }

  fun updateFailException(throwable: Throwable?) {
    failException = throwable
  }
}
