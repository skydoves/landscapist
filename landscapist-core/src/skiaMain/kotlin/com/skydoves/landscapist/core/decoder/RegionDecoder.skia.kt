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
package com.skydoves.landscapist.core.decoder

/**
 * Creates a region decoder for Skia platforms.
 *
 * Note: Skia doesn't have native region decoding support like Android's
 * BitmapRegionDecoder. For very large images on desktop/iOS, consider
 * using tiled image formats (like TIFF with tiles) or server-side
 * image processing that delivers pre-cropped regions.
 *
 * This implementation returns null, indicating region decoding is not
 * supported. Applications should fall back to full image loading with
 * appropriate downsampling.
 *
 * @param data The image data as a byte array.
 * @return null as region decoding is not natively supported on Skia.
 */
public actual fun createRegionDecoder(data: ByteArray): RegionDecoder? = null
