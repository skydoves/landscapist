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
package com.skydoves.landscapist

public enum class DataSource {
  /**
   * Represents an in-memory data source or cache (e.g. bitmap, ByteBuffer).
   */
  MEMORY,

  /**
   * Represents a disk-based data source (e.g. drawable resource, or File).
   */
  DISK,

  /**
   * Represents a network-based data source.
   */
  NETWORK,

  /**
   * Represents a local file system data source (File, content:// URI, file:// URI).
   */
  LOCAL,

  /**
   * Represents an Android resource data source (android.resource://, DrawableRes).
   */
  RESOURCE,

  /**
   * Represents a directly passed, already-decoded data source (Bitmap, Drawable).
   */
  INLINE,

  /**
   * Represents an unknown data source.
   */
  UNKNOWN,
}
