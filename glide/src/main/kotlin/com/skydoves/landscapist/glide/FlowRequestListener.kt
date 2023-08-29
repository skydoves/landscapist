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

import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.skydoves.landscapist.ImageLoadState
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.trySendBlocking

/**
 * FlowRequestListener is a [RequestListener] for receiving Glide image results from network and handle states.
 */
internal class FlowRequestListener(
  private val producerScope: ProducerScope<ImageLoadState>,
  private val failException: (Throwable?) -> Unit,
) : RequestListener<Any> {

  override fun onLoadFailed(
    e: GlideException?,
    model: Any?,
    target: Target<Any>,
    isFirstResource: Boolean,
  ): Boolean {
    failException.invoke(e)
    // return false so that the load failed will handle this.
    return false
  }

  override fun onResourceReady(
    resource: Any,
    model: Any,
    target: Target<Any>?,
    dataSource: DataSource,
    isFirstResource: Boolean,
  ): Boolean {
    producerScope.trySendBlocking(
      ImageLoadState.Success(
        data = resource,
        dataSource = dataSource.toDataSource(),
      ),
    )
    producerScope.channel.close()
    // return true so that the target doesn't receive the drawable.
    return true
  }
}

private fun DataSource.toDataSource(): com.skydoves.landscapist.DataSource = when (this) {
  DataSource.LOCAL -> com.skydoves.landscapist.DataSource.DISK
  DataSource.REMOTE -> com.skydoves.landscapist.DataSource.NETWORK
  DataSource.DATA_DISK_CACHE -> com.skydoves.landscapist.DataSource.DISK
  DataSource.RESOURCE_DISK_CACHE -> com.skydoves.landscapist.DataSource.DISK
  DataSource.MEMORY_CACHE -> com.skydoves.landscapist.DataSource.MEMORY
}
