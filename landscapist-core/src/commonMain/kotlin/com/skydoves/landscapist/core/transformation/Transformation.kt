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
package com.skydoves.landscapist.core.transformation

/**
 * Interface for image transformations.
 * Implementations can modify the decoded image before it's cached or displayed.
 */
public interface Transformation {
  /**
   * Unique key identifying this transformation.
   * Used for cache key generation.
   */
  public val key: String

  /**
   * Transforms the input image.
   *
   * @param input The input bitmap data (platform-specific type).
   * @return The transformed bitmap data.
   */
  public suspend fun transform(input: Any): Any
}
