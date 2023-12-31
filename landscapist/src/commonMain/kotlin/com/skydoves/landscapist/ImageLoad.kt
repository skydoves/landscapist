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
package com.skydoves.landscapist

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import com.skydoves.landscapist.constraints.Constrainable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * A common image loading model for fetching an image asynchronously and
 * run composable for displaying the image.
 *
 * @param recomposeKey request to execute image loading asynchronously.
 * @param executeImageRequest suspending lambda to execute an image loading request.
 * @param modifier adjust the drawing image layout or drawing decoration of the content.
 * @param content the image content to be loaded from executing for given states.
 */
@Composable
public fun <T : Any> ImageLoad(
  recomposeKey: T?,
  executeImageRequest: suspend () -> Flow<ImageLoadState>,
  modifier: Modifier = Modifier,
  imageOptions: ImageOptions,
  constrainable: Constrainable? = null,
  content: @Composable BoxWithConstraintsScope.(imageState: ImageLoadState) -> Unit,
) {
  var state by remember(recomposeKey, imageOptions) {
    mutableStateOf<ImageLoadState>(ImageLoadState.None)
  }
  LaunchedEffect(recomposeKey, imageOptions) {
    executeImageLoading(
      executeImageRequest,
    ).collect {
      state = it
    }
  }
  BoxWithConstraints(
    modifier = modifier.imageSemantics(imageOptions),
    propagateMinConstraints = true,
  ) {
    LaunchedEffect(key1 = recomposeKey, key2 = imageOptions) {
      val updatedConstraints = if (imageOptions.isValidSize) {
        val size = imageOptions.requestSize
        constraints.copy(
          minWidth = size.width,
          maxWidth = size.width,
          minHeight = size.height,
          maxHeight = size.height,
        )
      } else {
        constraints
      }
      constrainable?.setConstraints(updatedConstraints)
    }

    content(state)
  }
}

private suspend fun executeImageLoading(
  executeImageRequest: suspend () -> Flow<ImageLoadState>,
) = flow {
  // execute imager loading
  emitAll(executeImageRequest())
}.catch {
  // emit a failure loading state
  emit(ImageLoadState.Failure(null, null))
}.distinctUntilChanged().flowOn(Dispatchers.IO)

@InternalLandscapistApi
public val ZeroConstraints: Constraints = Constraints.fixed(0, 0)
