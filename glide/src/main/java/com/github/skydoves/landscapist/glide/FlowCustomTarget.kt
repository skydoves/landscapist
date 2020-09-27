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

package com.github.skydoves.landscapist.glide

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.FrameManager
import androidx.compose.ui.graphics.asImageAsset
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.github.skydoves.landscapist.ImageLoadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * FlowCustomTarget is a glide bitmap custom target which collects
 * [ImageLoadState] as a stateFlow.
 */
@ExperimentalCoroutinesApi
class FlowCustomTarget : CustomTarget<Bitmap>() {

  private val internalStateFlow = MutableStateFlow<ImageLoadState>(ImageLoadState.Loading(0f))
  val imageLoadStateFlow: StateFlow<ImageLoadState> get() = internalStateFlow

  override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
    FrameManager.ensureStarted()
    this.internalStateFlow.value = ImageLoadState.Success(resource.asImageAsset())
  }

  override fun onLoadFailed(errorDrawable: Drawable?) {
    super.onLoadFailed(errorDrawable)
    this.internalStateFlow.value = ImageLoadState.Failure(errorDrawable)
  }

  override fun onLoadCleared(placeholder: Drawable?) {
    val bitmap = (placeholder as? BitmapDrawable)?.bitmap
    this.internalStateFlow.value = ImageLoadState.Success(bitmap?.asImageAsset())
  }
}
