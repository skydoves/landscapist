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
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

internal class TestRequestListener(
  private val onComplete: (Any?) -> Unit
) : RequestListener<Bitmap> {
  override fun onLoadFailed(
    e: GlideException?,
    model: Any?,
    target: Target<Bitmap>?,
    isFirstResource: Boolean
  ): Boolean {
    onComplete(model)
    return false
  }

  override fun onResourceReady(
    resource: Bitmap?,
    model: Any?,
    target: Target<Bitmap>?,
    dataSource: DataSource?,
    isFirstResource: Boolean
  ): Boolean {
    onComplete(model)
    return false
  }
}
