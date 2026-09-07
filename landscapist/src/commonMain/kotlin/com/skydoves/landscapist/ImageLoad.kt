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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * A common image loading model for fetching an image asynchronously and
 * run composable for displaying the image.
 *
 * @param recomposeKey request to execute image loading asynchronously.
 * @param executeImageRequest suspending lambda to execute an image loading request.
 * @param modifier adjust the drawing image layout or drawing decoration of the content.
 * @param content the image content to be loaded from executing for given states.
 */
@OptIn(InternalLandscapistApi::class)
@Composable
public fun <T : Any> ImageLoad(
  recomposeKey: T?,
  executeImageRequest: suspend () -> Flow<ImageLoadState>,
  modifier: Modifier = Modifier,
  imageOptions: ImageOptions,
  constrainable: Constrainable? = null,
  content: @Composable BoxWithConstraintsScope.(imageState: ImageLoadState) -> Unit,
) {
  // Use loadingOptionsKey to only trigger reload when loading-related properties change.
  // Rendering properties (colorFilter, alpha, alignment, contentScale, contentDescription)
  // should not cause image reloads.
  val loadingKey = imageOptions.loadingOptionsKey

  var state by remember(recomposeKey, loadingKey) {
    mutableStateOf<ImageLoadState>(ImageLoadState.None)
  }

  LaunchedEffect(key1 = recomposeKey, key2 = loadingKey) {
    executeImageLoading(executeImageRequest).collect { next ->
      // A backend that restarts a request emits its loading state again. Blanking an image the
      // user can already see is what makes images blink on tab switches and shared element
      // transitions, so a resolved image holds until the restart resolves. ImageLoadState.None is
      // not filtered: it means the backend released the bitmap, so it must stop being drawn.
      if (next is ImageLoadState.Loading && state is ImageLoadState.Success) return@collect
      state = next
    }
  }

  BoxWithConstraints(
    modifier = modifier.imageSemantics(imageOptions),
    propagateMinConstraints = true,
  ) {
    LaunchedEffect(key1 = recomposeKey, key2 = loadingKey) {
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

/**
 * Runs the backend's request flow.
 *
 * No loading state is emitted up front, and no dispatcher is imposed. Every backend signals its own
 * loading state and does its fetching and decoding on its own threads, so hopping to a background
 * dispatcher and back only delayed each emission by a frame, which showed as a blink whenever the
 * image was already cached.
 */
private fun executeImageLoading(
  executeImageRequest: suspend () -> Flow<ImageLoadState>,
) = flow {
  emitAll(executeImageRequest())
}.catch { throwable ->
  emit(ImageLoadState.Failure(null, throwable))
}.distinctUntilChanged()

@InternalLandscapistApi
public val ZeroConstraints: Constraints = Constraints.fixed(0, 0)
