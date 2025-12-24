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
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import com.skydoves.landscapist.DataSource
import com.skydoves.landscapist.ProvideImageSourceFile

/**
 * Android implementation that provides the image source file for sub-sampling support.
 */
@Composable
internal actual fun ProvideCoilSourceFile(
  imageLoader: ImageLoader,
  imageModel: Any?,
  dataSource: DataSource?,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current

  val sourceFile = rememberImageSourceFile(
    context = context,
    imageLoader = imageLoader,
    imageModel = imageModel,
    dataSource = dataSource,
  )

  ProvideImageSourceFile(file = sourceFile) {
    content()
  }
}
