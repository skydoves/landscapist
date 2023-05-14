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
@file:OptIn(ExperimentalComposeUiApi::class)

package com.skydoves.benchmark.landscapist.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.skydoves.landscapist.animation.circular.CircularRevealPlugin
import com.skydoves.landscapist.animation.crossfade.CrossfadePlugin
import com.skydoves.landscapist.components.LocalImageComponent
import com.skydoves.landscapist.components.imageComponent
import com.skydoves.landscapist.palette.PalettePlugin
import com.skydoves.landscapist.placeholder.placeholder.PlaceholderPlugin
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
import com.skydoves.landscapist.transformation.blur.BlurTransformationPlugin

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      val imageComponent = imageComponent {
        +PlaceholderPlugin.Loading(painterResource(id = R.drawable.poster))
        +PlaceholderPlugin.Failure(painterResource(id = R.drawable.poster))
        +ShimmerPlugin(
          baseColor = Color.Transparent,
          highlightColor = Color.Transparent,
        )
        +CrossfadePlugin()
        +CircularRevealPlugin()
        +BlurTransformationPlugin()
        +PalettePlugin()
      }

      CompositionLocalProvider(LocalImageComponent provides imageComponent) {
        Column(
          modifier = Modifier.semantics {
            testTagsAsResourceId = true
          },
        ) {
          CoilImageProfiles()
          GlideImageProfiles()
          FrescoImageProfiles()
          FrescoWebSupportProfiles()
        }
      }
    }
  }
}
