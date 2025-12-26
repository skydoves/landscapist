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
package com.skydoves.landscapist.core.cache

import java.lang.ref.WeakReference

/**
 * JVM implementation using java.lang.ref.WeakReference.
 */
public actual class WeakRef<T : Any> actual constructor(referent: T) {
  private val ref = WeakReference(referent)

  public actual fun get(): T? = ref.get()

  public actual fun clear() {
    ref.clear()
  }
}
