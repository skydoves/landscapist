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
package com.skydoves.landscapist.image

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Converts platform-specific bitmap data to Compose [ImageBitmap].
 *
 * @param data The platform-specific bitmap (Bitmap on Android, BufferedImage on Desktop,
 *             RawImageData on Apple/Wasm).
 * @return The converted [ImageBitmap], or null if conversion failed.
 */
public expect fun convertToImageBitmap(data: Any): ImageBitmap?

/**
 * Checks if the given data is a pre-decoded bitmap type that can be rendered directly.
 * This includes platform-specific Bitmap types and Compose ImageBitmap.
 *
 * @param data The data to check.
 * @return true if the data is a bitmap type, false otherwise.
 */
public expect fun isBitmapType(data: Any?): Boolean

/**
 * Gets the width of a bitmap. Returns 0 if the data is not a bitmap type.
 *
 * @param data The bitmap data.
 * @return The width in pixels, or 0 if not a bitmap.
 */
public expect fun getBitmapWidth(data: Any?): Int

/**
 * Gets the height of a bitmap. Returns 0 if the data is not a bitmap type.
 *
 * @param data The bitmap data.
 * @return The height in pixels, or 0 if not a bitmap.
 */
public expect fun getBitmapHeight(data: Any?): Int
