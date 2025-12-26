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
package com.skydoves.landscapist.core.model

/**
 * Represents the caching policy for image loading operations.
 */
public enum class CachePolicy {
  /** Cache is enabled for both reading and writing. */
  ENABLED,

  /** Cache is only used for reading, not writing. */
  READ_ONLY,

  /** Cache is only used for writing, not reading. */
  WRITE_ONLY,

  /** Cache is completely disabled. */
  DISABLED,
  ;

  /** Whether reading from cache is enabled. */
  public val readEnabled: Boolean
    get() = this == ENABLED || this == READ_ONLY

  /** Whether writing to cache is enabled. */
  public val writeEnabled: Boolean
    get() = this == ENABLED || this == WRITE_ONLY
}
