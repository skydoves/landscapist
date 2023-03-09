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

import android.graphics.drawable.Drawable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import com.bumptech.glide.request.Request
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.SizeReadyCallback
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.skydoves.landscapist.ImageLoadState
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.constraints.Constrainable
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.trySendBlocking

/**
 * FlowCustomTarget is a [CustomTarget] for receiving Glide image results from network and handle states.
 */
internal class FlowCustomTarget constructor(
  private val imageOptions: ImageOptions
) : Target<Any>, Constrainable {

  private val lock: Any = Any()

  private var producerScope: ProducerScope<ImageLoadState>? = null

  private var resolvedSize: IntSize? = null

  private var currentRequest: Request? = null

  private val sizeReadyCallbacks = mutableListOf<SizeReadyCallback>()

  private var failException: Throwable? = null

  /** onResourceReady will be handled by [FlowRequestListener]. */
  override fun onResourceReady(resource: Any, transition: Transition<in Any>?) = Unit

  override fun onLoadStarted(placeholder: Drawable?) {
    producerScope?.trySendBlocking(ImageLoadState.Loading)
  }

  override fun onLoadFailed(errorDrawable: Drawable?) {
    producerScope?.trySendBlocking(
      ImageLoadState.Failure(
        data = errorDrawable,
        reason = failException
      )
    )
    producerScope?.channel?.close()
  }

  override fun onLoadCleared(placeholder: Drawable?) {
    // Glide wants to free up the resource, so we need to clear
    // the result, otherwise we might draw a recycled bitmap later.
    producerScope?.trySendBlocking(ImageLoadState.None)
    producerScope?.channel?.close()
  }

  override fun onStart() = Unit
  override fun onStop() = Unit
  override fun onDestroy() = Unit

  override fun getSize(cb: SizeReadyCallback) {
    val localResolvedSize = resolvedSize
    if (localResolvedSize != null) {
      cb.onSizeReady(localResolvedSize.width, localResolvedSize.height)
      return
    }

    synchronized(lock) {
      val lockedResolvedSize = resolvedSize
      if (lockedResolvedSize != null) {
        cb.onSizeReady(lockedResolvedSize.width, lockedResolvedSize.height)
      } else {
        sizeReadyCallbacks.add(cb)
      }
    }
  }

  override fun removeCallback(cb: SizeReadyCallback) {
    synchronized(lock) { sizeReadyCallbacks.remove(cb) }
  }

  override fun setRequest(request: Request?) {
    currentRequest = request
  }

  override fun getRequest(): Request? = currentRequest

  override fun setConstraints(constraints: Constraints) {
    val localResolvedSize = constraints.inferredIntSize
    val callbacksToNotify: List<SizeReadyCallback>
    synchronized(lock) {
      resolvedSize = localResolvedSize
      callbacksToNotify = ArrayList(sizeReadyCallbacks)
      sizeReadyCallbacks.clear()
    }
    callbacksToNotify.forEach {
      it.onSizeReady(localResolvedSize.width, localResolvedSize.height)
    }
  }

  fun setProducerScope(producerScope: ProducerScope<ImageLoadState>) {
    this.producerScope = producerScope
  }

  fun updateFailException(throwable: Throwable?) {
    failException = throwable
  }

  private val Constraints.inferredIntSize: IntSize
    get() {
      if (imageOptions.isValidSize) return imageOptions.requestSize
      val width =
        if (hasBoundedWidth && maxWidth.isValidGlideDimension) maxWidth else Target.SIZE_ORIGINAL
      val height =
        if (hasBoundedHeight && maxHeight.isValidGlideDimension) maxHeight else Target.SIZE_ORIGINAL
      return IntSize(width, height)
    }

  private val Int.isValidGlideDimension: Boolean
    get() = this > 0 || this == Target.SIZE_ORIGINAL
}
