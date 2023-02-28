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
package com.skydoves.landscapist.fresco

import android.graphics.Bitmap
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSource
import com.facebook.drawee.backends.pipeline.info.ImageOrigin
import com.facebook.imagepipeline.datasource.BaseBitmapReferenceDataSubscriber
import com.facebook.imagepipeline.image.CloseableImage
import com.skydoves.landscapist.ImageLoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * FlowBaseBitmapDataSubscriber is a fresco bitmap subscriber which collects
 * [ImageLoadState] as a stateFlow.
 */
internal class FlowBaseBitmapDataSubscriber : BaseBitmapReferenceDataSubscriber() {

  private val internalStateFlow = MutableStateFlow<ImageLoadState>(ImageLoadState.None)
  val imageLoadStateFlow: StateFlow<ImageLoadState> get() = internalStateFlow

  private var imageOrigin: Int = ImageOrigin.UNKNOWN

  private var successBitmapReference: CloseableReference<Bitmap>? = null
  private var failureBitmapReference: DataSource<CloseableReference<CloseableImage>>? = null

  override fun onNewResultImpl(bitmapReference: CloseableReference<Bitmap>?) {
    this.successBitmapReference = bitmapReference
    this.internalStateFlow.value = ImageLoadState.Success(
      data = bitmapReference?.cloneOrNull(),
      dataSource = imageOrigin.toDataSource()
    )
  }

  override fun onFailureImpl(dataSource: DataSource<CloseableReference<CloseableImage>>) {
    this.failureBitmapReference = dataSource
    this.internalStateFlow.value = ImageLoadState.Failure(
      data = dataSource,
      reason = dataSource.failureCause
    )
  }

  override fun onProgressUpdate(dataSource: DataSource<CloseableReference<CloseableImage>>) {
    super.onProgressUpdate(dataSource)

    if (internalStateFlow.value == ImageLoadState.None) {
      this.internalStateFlow.value = ImageLoadState.Loading
    }
  }

  fun clearBitmapResource() {
    successBitmapReference?.close()
    failureBitmapReference?.close()
  }

  fun updateImageOrigin(imageOrigin: Int) {
    this.imageOrigin = imageOrigin
  }
}

private fun Int.toDataSource(): com.skydoves.landscapist.DataSource = when (this) {
  ImageOrigin.DISK -> com.skydoves.landscapist.DataSource.DISK
  ImageOrigin.NETWORK -> com.skydoves.landscapist.DataSource.NETWORK
  ImageOrigin.LOCAL -> com.skydoves.landscapist.DataSource.DISK
  ImageOrigin.MEMORY_BITMAP -> com.skydoves.landscapist.DataSource.MEMORY
  ImageOrigin.MEMORY_BITMAP_SHORTCUT -> com.skydoves.landscapist.DataSource.MEMORY
  ImageOrigin.MEMORY_ENCODED -> com.skydoves.landscapist.DataSource.MEMORY
  ImageOrigin.UNKNOWN -> com.skydoves.landscapist.DataSource.UNKNOWN
  else -> com.skydoves.landscapist.DataSource.UNKNOWN
}
