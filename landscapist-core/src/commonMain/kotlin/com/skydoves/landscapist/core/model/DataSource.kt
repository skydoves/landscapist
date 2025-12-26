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
 * Represents the source from which an image was loaded.
 */
public enum class DataSource {
  /** Image was loaded from memory cache. */
  MEMORY,

  /** Image was loaded from disk cache. */
  DISK,

  /** Image was loaded from network. */
  NETWORK,

  /** Image was loaded from local file system (File, content:// URI, file:// URI). */
  LOCAL,

  /** Image was loaded from Android resources (android.resource://, DrawableRes). */
  RESOURCE,

  /** Image was passed directly as already-decoded data (Bitmap, Drawable). */
  INLINE,

  /** Image source is unknown. */
  UNKNOWN,
}
