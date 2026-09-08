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
package com.skydoves.landscapist.crossfade

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.skydoves.landscapist.InternalLandscapistApi
import kotlinx.coroutines.delay

/**
 * A high-level composable that animates between different content states using a
 * sophisticated crossfade effect that includes brightness and saturation animations.
 *
 * When the [targetState] changes, the old content will fade out while the new content
 * fades in, using the underlying `fadeInWithEffect` and `fadeOutWithEffect` modifiers.
 * The animation can be disabled by setting [enabled] to false.
 *
 * @param T The type of the state object.
 * @param targetState The state that drives the content to be displayed.
 * @param modifier Modifier to be applied to the container.
 * @param durationMs The duration of the fade-in and fade-out animations.
 * @param enabled A boolean to enable or disable the animation. If false, the content
 * will switch instantly. Defaults to true.
 * @param contentKey A factory for keys that are used to identify items. This is crucial
 * for managing animations correctly when the state `T` is not a simple primitive.
 * @param content The composable lambda that displays the content for a given state.
 */
@Composable
@InternalLandscapistApi
public fun <T> CrossfadeWithEffect(
  targetState: T,
  modifier: Modifier = Modifier,
  durationMs: Int = 250,
  enabled: Boolean = true,
  contentKey: (T) -> Any? = { it },
  content: @Composable (T) -> Unit,
) {
  // Nothing is tracked when the animation is off, which is the default: no crossfade plugin means
  // no fade out to keep alive, and a snapshot state list per image is not free.
  if (!enabled) {
    Box(modifier = modifier, propagateMinConstraints = true) {
      key(contentKey(targetState)) {
        content(targetState)
      }
    }
    return
  }

  // Seeded with the state this composable entered composition with, for two reasons. Waiting for
  // the effect below to add it leaves the first frame empty, and content that was already resolved
  // when the composable appeared (an image read straight from the memory cache, say) has nothing to
  // fade in from. Only content that arrives later animates.
  val currentlyVisibleItems = remember { mutableStateListOf(targetState) }
  val initialContentKey = remember { contentKey(targetState) }
  // Once something else has been the target, the initial state has stopped being the one that was
  // already on screen, so coming back to it animates like any other arrival.
  var initialContentReplaced by remember { mutableStateOf(false) }

  LaunchedEffect(targetState) {
    val key = contentKey(targetState)
    if (key != initialContentKey) {
      initialContentReplaced = true
    }
    if (!currentlyVisibleItems.any { contentKey(it) == key }) {
      currentlyVisibleItems.add(targetState)
    }
  }

  Box(modifier = modifier, propagateMinConstraints = true) {
    currentlyVisibleItems.forEach { state ->
      key(contentKey(state)) {
        val stateKey = contentKey(state)
        val isTarget = stateKey == contentKey(targetState)

        val animationModifier = when {
          !isTarget -> Modifier.fadeOutWithEffect(key = Unit, durationMs = durationMs)
          !initialContentReplaced && stateKey == initialContentKey -> Modifier
          else -> Modifier.fadeInWithEffect(key = stateKey ?: Unit, durationMs = durationMs)
        }

        if (!isTarget) {
          LaunchedEffect(Unit) {
            delay(durationMs.toLong())
            currentlyVisibleItems.remove(state)
          }
        }

        Box(modifier = animationModifier, propagateMinConstraints = true) {
          content(state)
        }
      }
    }
  }
}
