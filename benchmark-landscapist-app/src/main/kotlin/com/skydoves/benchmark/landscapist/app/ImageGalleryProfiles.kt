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
package com.skydoves.benchmark.landscapist.app

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.components.LocalImageComponent
import com.skydoves.landscapist.gallery.ImageGallery

private val galleryImageUrls = listOf(
  "https://user-images.githubusercontent.com/" +
    "24237865/75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg",
  "https://user-images.githubusercontent.com/" +
    "24237865/75087934-5b850900-553e-11ea-92d7-b7c7e7e09bb0.jpg",
  "https://user-images.githubusercontent.com/" +
    "24237865/75087930-5a53dc00-553e-11ea-8eff-c4e98d76a51e.jpg",
  "https://user-images.githubusercontent.com/" +
    "24237865/75087932-5aec7280-553e-11ea-9301-3b12ddaae0a7.jpg",
  "https://user-images.githubusercontent.com/" +
    "24237865/95545537-1bc15200-0a39-11eb-883d-644f564da5d3.png",
  "https://user-images.githubusercontent.com/" +
    "24237865/95545538-1cf27f00-0a39-11eb-83dd-ef9b8c6a74cb.png",
)

@Composable
fun ImageGalleryProfiles() {
  ImageGallery(
    images = galleryImageUrls,
    modifier = Modifier.height(360.dp),
    component = LocalImageComponent.current,
    imageOptions = ImageOptions(tag = "ImageGallery"),
  )
}
