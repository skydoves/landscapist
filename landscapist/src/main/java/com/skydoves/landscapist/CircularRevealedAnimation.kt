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

package com.skydoves.landscapist

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FloatPropKey
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.transitionDefinition
import androidx.compose.animation.core.tween

/**
 * CircularRevealedAnimation is an animation for animating a clipping circle to reveal an image.
 * The animation has two states Loaded, Empty.
 */
internal object CircularRevealedAnimation {

  /** Common interface of the animation states. */
  enum class State {
    /** animation not started. */
    None,

    /** animation finished. */
    Finished
  }

  val Radius = FloatPropKey()

  /** definitions of the specific animating values based on animation states.  */
  fun definition(durationMillis: Int, easing: Easing = LinearEasing) = transitionDefinition<State> {
    state(State.None) {
      this[Radius] = 0f
    }
    state(State.Finished) {
      this[Radius] = 1f
    }

    transition {
      Radius using tween(durationMillis = durationMillis, easing = easing)
    }
  }
}
