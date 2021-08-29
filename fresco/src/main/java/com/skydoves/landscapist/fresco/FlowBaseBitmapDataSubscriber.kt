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

package com.skydoves.landscapist.fresco

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSource
import com.facebook.imagepipeline.datasource.BaseBitmapReferenceDataSubscriber
import com.facebook.imagepipeline.image.CloseableImage
import com.skydoves.landscapist.ImageLoadState
import com.skydoves.landscapist.palette.BitmapPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * FlowBaseBitmapDataSubscriber is a fresco bitmap subscriber which collects
 * [ImageLoadState] as a stateFlow.
 */
internal class FlowBaseBitmapDataSubscriber(
  private val observeLoadingProcess: Boolean,
  private val bitmapPalette: BitmapPalette?
) : BaseBitmapReferenceDataSubscriber() {

  private val internalStateFlow = MutableStateFlow<ImageLoadState>(ImageLoadState.None)
  val imageLoadStateFlow: StateFlow<ImageLoadState> get() = internalStateFlow

  override fun onNewResultImpl(bitmapReference: CloseableReference<Bitmap>?) {
    this.internalStateFlow.value = ImageLoadState.Success(bitmapReference?.get()?.asImageBitmap())
    this.bitmapPalette?.generate(bitmapReference?.get())
    bitmapReference?.close()
  }

  override fun onFailureImpl(dataSource: DataSource<CloseableReference<CloseableImage>>) {
    this.internalStateFlow.value = ImageLoadState.Failure(dataSource)
    dataSource.close()
  }

  override fun onProgressUpdate(dataSource: DataSource<CloseableReference<CloseableImage>>) {
    super.onProgressUpdate(dataSource)

    /** collect progress values whenever progress is changed if the [observeLoadingProcess] is true. */
    if (internalStateFlow.value == ImageLoadState.None || observeLoadingProcess) {
      this.internalStateFlow.value = ImageLoadState.Loading(dataSource.progress)
    }
    dataSource.close()
  }
}
