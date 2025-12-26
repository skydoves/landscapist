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
package com.skydoves.landscapist.core

/**
 * Strategy for downsampling images during decoding to reduce memory usage.
 */
public enum class DownsamplingStrategy {
  /**
   * Automatically determine the best downsampling based on target size and max bitmap size.
   * This is the default strategy that balances quality and memory.
   */
  AUTO,

  /**
   * No downsampling - decode at original size.
   * Warning: May cause OOM for large images.
   */
  NONE,

  /**
   * Downsample to exactly fit within the target dimensions.
   * The resulting image will be at most the target size.
   */
  FIT,

  /**
   * Downsample so the image fills the target dimensions (may exceed target in one dimension).
   * Useful for images that will be center-cropped.
   */
  FILL,
}

/**
 * Configuration for bitmap decoding behavior.
 *
 * @property strategy The downsampling strategy to use.
 * @property maxBitmapSize Maximum dimension for decoded bitmaps.
 * @property allowRgb565 Whether to use RGB_565 format for images without alpha (saves 50% memory).
 * @property enableProgressiveDecoding Whether to enable progressive decoding for JPEG images.
 * @property allowHardware Whether to allow hardware bitmaps (Android only, requires API 26+).
 *                         Hardware bitmaps are stored in GPU memory for faster rendering
 *                         but cannot be modified or accessed on the CPU.
 */
public data class BitmapConfig(
  val strategy: DownsamplingStrategy = DownsamplingStrategy.AUTO,
  val maxBitmapSize: Int = LandscapistConfig.DEFAULT_MAX_BITMAP_SIZE,
  val allowRgb565: Boolean = false,
  val enableProgressiveDecoding: Boolean = false,
  val allowHardware: Boolean = true,
)
