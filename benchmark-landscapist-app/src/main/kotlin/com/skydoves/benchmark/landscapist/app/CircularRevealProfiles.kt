/*
 * Designed and developed by 2020-2022 skydoves (Jaewoong Eum)
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

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.CircularReveal
import com.skydoves.landscapist.CircularRevealImage
import com.skydoves.landscapist.DefaultCircularRevealDuration

@Composable
fun CircularRevealProfiles(
  imageBitmap: ImageBitmap
) {
  val circularReveal = CircularReveal(
    duration = DefaultCircularRevealDuration,
    onFinishListener = { }
  )
  CircularRevealImage(
    bitmap = imageBitmap,
    modifier = Modifier.size(100.dp),
    bitmapPainter = painterResource(R.drawable.poster),
    alignment = Alignment.Center,
    contentScale = ContentScale.Crop,
    contentDescription = null,
    alpha = DefaultAlpha,
    colorFilter = null,
    circularReveal = circularReveal
  )
}
