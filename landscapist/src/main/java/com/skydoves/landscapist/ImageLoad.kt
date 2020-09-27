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

package com.github.skydoves.landscapist

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.awaitDispose
import androidx.compose.runtime.getValue
import androidx.compose.runtime.launchInComposition
import androidx.compose.runtime.setValue
import androidx.compose.runtime.stateFor
import androidx.compose.ui.Modifier
import androidx.compose.ui.WithConstraints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * A common image loading model for fetching an image asynchronously and
 * run composable for displaying the image.
 */
@Composable
fun <T : Any> ImageLoad(
  imageRequest: T,
  executeImageRequest: suspend () -> Flow<ImageLoadState>,
  disposeImageRequest: () -> Unit,
  modifier: Modifier = Modifier.fillMaxWidth(),
  content: @Composable (imageState: ImageLoadState) -> Unit
) {
  var state by stateFor<ImageLoadState>(imageRequest) { ImageLoadState.None }
  launchInComposition(imageRequest) {
    executeImageLoading(
      executeImageRequest
    ).collect {
      state = it
    }
    awaitDispose {
      disposeImageRequest()
    }
  }
  WithConstraints(modifier) {
    content(state)
  }
}

private suspend fun executeImageLoading(
  executeImageRequest: suspend () -> Flow<ImageLoadState>
) = flow {
  // execute imager loading
  emitAll(executeImageRequest())
}.catch {
  emit(ImageLoadState.Failure(null))
}.flowOn(Dispatchers.IO)
