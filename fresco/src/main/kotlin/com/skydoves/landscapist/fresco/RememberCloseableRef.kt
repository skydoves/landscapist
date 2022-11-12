/*
 * Designed and developed by 2020-2022 skydoves (Jaewoong Eum)
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import com.facebook.common.references.CloseableReference

/**
 * Remember the given [CloseableReference] in the current composition.
 *
 * The [CloseableReference] will be closed when the object leaves the composition.
 *
 * @param ref the [CloseableReference] to be remembered
 * @return the value contained in the reference, as per [CloseableReference.get]
 */
@Composable
internal fun <T> rememberCloseableRef(ref: CloseableReference<T>): T {
  return remember(ref) { CloseableRefObserver(ref) }.value
}

private class CloseableRefObserver<T>(val ref: CloseableReference<T>) : RememberObserver {

  val value: T
    get() = ref.get()

  override fun onRemembered() {
    // nothing to do
  }

  override fun onAbandoned() {
    ref.close()
  }

  override fun onForgotten() {
    ref.close()
  }
}
