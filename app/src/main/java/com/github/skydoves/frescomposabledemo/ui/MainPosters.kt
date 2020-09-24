/*
 * Designed and developed by 2020 skydoves (Jaewoong Eum)
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

package com.github.skydoves.frescomposabledemo.ui

import androidx.compose.foundation.ScrollableColumn
import androidx.compose.foundation.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ConstraintLayout
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.ripple.RippleIndication
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.ui.tooling.preview.Preview
import com.github.skydoves.frescomposabledemo.model.MockUtil
import com.github.skydoves.frescomposabledemo.model.Poster
import com.github.skydoves.frescomposabledemo.theme.DisneyComposeTheme
import com.github.skydoves.frescomposabledemo.theme.purple500
import com.skydoves.frescomposable.FrescoImage

@Composable
fun DisneyPosters(
  posters: List<Poster>,
  modifier: Modifier = Modifier
) {
  ScrollableColumn(
    modifier = modifier
      .background(MaterialTheme.colors.background)
  ) {
    StaggeredVerticalGrid(
      maxColumnWidth = 220.dp,
      modifier = Modifier.padding(4.dp)
    ) {
      posters.forEach { poster ->
        HomePoster(poster = poster)
      }
    }
  }
}

@Composable
fun HomePoster(
  poster: Poster,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier.padding(4.dp),
    color = MaterialTheme.colors.onBackground,
    elevation = 8.dp,
    shape = RoundedCornerShape(8.dp)
  ) {
    ConstraintLayout(
      modifier = Modifier.clickable(
        onClick = { },
        indication = RippleIndication(color = purple500)
      )
    ) {
      val (image, title, content, message) = createRefs()
      FrescoImage(
        imageUrl = poster.poster,
        modifier = Modifier.constrainAs(image) {
          centerHorizontallyTo(parent)
          top.linkTo(parent.top)
        }.aspectRatio(0.8f),
        loading = {
          ConstraintLayout(
            modifier = Modifier.fillMaxSize()
          ) {
            val indicator = createRef()
            CircularProgressIndicator(
              modifier = Modifier.constrainAs(indicator) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
              }
            )
          }
        },
        failure = {
          ConstraintLayout(
            modifier = Modifier.fillMaxSize()
          ) {
            Text(
              text = "image request failed.",
              modifier = Modifier.constrainAs(message) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
              }
            )
          }
        }
      )
      Text(
        text = poster.name,
        style = MaterialTheme.typography.h2,
        textAlign = TextAlign.Center,
        modifier = Modifier.constrainAs(title) {
          centerHorizontallyTo(parent)
          top.linkTo(image.bottom)
        }.padding(8.dp)
      )
      Text(
        text = poster.playtime,
        style = MaterialTheme.typography.body1,
        textAlign = TextAlign.Center,
        modifier = Modifier.constrainAs(content) {
          centerHorizontallyTo(parent)
          top.linkTo(title.bottom)
        }.padding(horizontal = 8.dp)
          .padding(bottom = 12.dp)
      )
    }
  }
}

@Preview
@Composable
fun HomePosterPreviewLight() {
  DisneyComposeTheme(darkTheme = false) {
    HomePoster(
      poster = MockUtil.getMockPoster()
    )
  }
}

@Preview
@Composable
fun HomePosterPreviewDark() {
  DisneyComposeTheme(darkTheme = true) {
    HomePoster(
      poster = MockUtil.getMockPoster()
    )
  }
}
