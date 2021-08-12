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

package com.skydoves.landscapist.glide

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asImageBitmap
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.skydoves.landscapist.ImageLoadState
import com.skydoves.landscapist.palette.BitmapPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * FlowCustomTarget is a glide bitmap custom target which collects
 * [ImageLoadState] as a stateFlow.
 */
internal class FlowCustomTarget constructor(
  private val bitmapPalette: BitmapPalette?
) : CustomTarget<Bitmap>(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL) {

  private val internalStateFlow = MutableStateFlow<ImageLoadState>(ImageLoadState.None)
  val imageLoadStateFlow: StateFlow<ImageLoadState> get() = internalStateFlow

  override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
    bitmapPalette?.generate(resource)
    this.internalStateFlow.value = ImageLoadState.Success(resource.asImageBitmap())
  }

  override fun onLoadStarted(placeholder: Drawable?) {
    super.onLoadStarted(placeholder)
    this.internalStateFlow.value = ImageLoadState.Loading(0f)
  }

  override fun onLoadFailed(errorDrawable: Drawable?) {
    super.onLoadFailed(errorDrawable)
    this.internalStateFlow.value = ImageLoadState.Failure(errorDrawable)
  }

  override fun onLoadCleared(placeholder: Drawable?) {
    val bitmap = (placeholder as? BitmapDrawable)?.bitmap
    this.internalStateFlow.value = ImageLoadState.Success(bitmap?.asImageBitmap())
  }
}
