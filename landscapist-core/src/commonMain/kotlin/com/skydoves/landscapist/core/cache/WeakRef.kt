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
 * Platform-agnostic weak reference wrapper.
 * Used for the weak reference layer in memory cache.
 */
public expect class WeakRef<T : Any>(referent: T) {
  /**
   * Gets the referenced value, or null if it has been garbage collected.
   */
  public fun get(): T?

  /**
   * Clears the reference.
   */
  public fun clear()
}
