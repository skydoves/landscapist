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
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.skydoves.landscapist.ImageLoadState
import com.skydoves.landscapist.palette.BitmapPalette
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.trySendBlocking

/**
 * FlowRequestListener is a [RequestListener] for receiving Glide image results from network and handle states.
 */
internal class FlowRequestListener constructor(
  private val producerScope: ProducerScope<ImageLoadState>,
  private val bitmapPalette: BitmapPalette?
) : RequestListener<Drawable> {

  override fun onLoadFailed(
    e: GlideException?,
    model: Any?,
    target: Target<Drawable>?,
    isFirstResource: Boolean
  ): Boolean {
    // return false so that the load failed will handle this.
    return false
  }

  override fun onResourceReady(
    resource: Drawable?,
    model: Any?,
    target: Target<Drawable>?,
    dataSource: DataSource?,
    isFirstResource: Boolean
  ): Boolean {
    producerScope.trySendBlocking(ImageLoadState.Success(resource))
    producerScope.channel.close()
    bitmapPalette?.generate(resource?.toBitmap())
    // return true so that the target doesn't receive the drawable.
    return true
  }
}
