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
package com.skydoves.landscapist.coil3

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import com.skydoves.landscapist.DataSource

/**
 * Platform-specific wrapper that provides image source file information for sub-sampling support.
 *
 * On Android, this retrieves the cached file and provides it via [LocalImageSourceFile].
 * On other platforms, this is a no-op and just renders the content.
 *
 * @param imageLoader The Coil3 ImageLoader.
 * @param imageModel The image model (URL, URI, File, etc.)
 * @param dataSource The data source from Coil3.
 * @param content The content to wrap.
 */
@Composable
internal expect fun ProvideCoilSourceFile(
  imageLoader: ImageLoader,
  imageModel: Any?,
  dataSource: DataSource?,
  content: @Composable () -> Unit,
)
