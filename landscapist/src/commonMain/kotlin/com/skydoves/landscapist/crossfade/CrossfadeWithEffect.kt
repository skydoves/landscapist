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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
  val currentlyVisibleItems = remember { mutableStateListOf<T>() }

  LaunchedEffect(targetState) {
    if (!currentlyVisibleItems.any { contentKey(it) == contentKey(targetState) }) {
      currentlyVisibleItems.add(targetState)
    }
  }

  Box(modifier) {
    if (enabled) {
      currentlyVisibleItems.forEach { state ->
        key(contentKey(state)) {
          val isTarget = contentKey(state) == contentKey(targetState)

          val animationModifier = if (isTarget) {
            Modifier.fadeInWithEffect(key = contentKey(state) ?: Unit, durationMs = durationMs)
          } else {
            Modifier.fadeOutWithEffect(key = Unit, durationMs = durationMs)
          }

          if (!isTarget) {
            LaunchedEffect(Unit) {
              delay(durationMs.toLong())
              currentlyVisibleItems.remove(state)
            }
          }

          Box(modifier = animationModifier) {
            content(state)
          }
        }
      }
    } else {
      if (currentlyVisibleItems.size > 1 || currentlyVisibleItems.firstOrNull() != targetState) {
        currentlyVisibleItems.retainAll { contentKey(it) == contentKey(targetState) }
        if (currentlyVisibleItems.isEmpty()) {
          currentlyVisibleItems.add(targetState)
        }
      }

      // Render the content directly without any animation modifiers.
      key(contentKey(targetState)) {
        content(targetState)
      }
    }
  }
}
