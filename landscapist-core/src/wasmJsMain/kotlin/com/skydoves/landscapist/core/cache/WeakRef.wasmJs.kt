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

/**
 * WasmJs implementation using a simple strong reference.
 * JavaScript WeakRef is not easily usable from Kotlin/Wasm,
 * so we fall back to a simple strong reference.
 */
public actual class WeakRef<T : Any> actual constructor(referent: T) {
  private var ref: T? = referent

  public actual fun get(): T? = ref

  public actual fun clear() {
    ref = null
  }
}
