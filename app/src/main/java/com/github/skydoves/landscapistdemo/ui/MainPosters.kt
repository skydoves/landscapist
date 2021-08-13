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

package com.github.skydoves.landscapistdemo.ui

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.palette.graphics.Palette
import com.github.skydoves.landscapistdemo.model.MockUtil
import com.github.skydoves.landscapistdemo.model.Poster
import com.github.skydoves.landscapistdemo.theme.DisneyComposeTheme
import com.github.skydoves.landscapistdemo.theme.background800
import com.github.skydoves.landscapistdemo.theme.shimmerHighLight
import com.skydoves.landscapist.CircularReveal
import com.skydoves.landscapist.ShimmerParams
import com.skydoves.landscapist.fresco.FrescoImage
import com.skydoves.landscapist.glide.GlideImage
import com.skydoves.landscapist.palette.BitmapPalette

@Composable
fun DisneyPosters(
  posters: List<Poster>,
  vm: MainViewModel
) {
  val poster = vm.poster.observeAsState()
  Column(Modifier.verticalScroll(rememberScrollState())) {
    LazyRow {
      item {
        Box(
          modifier = Modifier.padding(
            start = 16.dp,
            bottom = 16.dp,
            top = 16.dp,
            end = 8.dp
          )
        ) {
          Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "Favorite",
            modifier = Modifier.size(50.dp),
            tint = Color.Red
          )
        }
      }
      items(posters) { poster ->
        PosterItem(poster, vm)
      }
    }
    SelectedPoster(poster.value)
  }
}

@Composable
fun PosterItem(
  poster: Poster,
  vm: MainViewModel
) {
  Card(modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
    GlideImage(
      imageModel = poster.poster,
      modifier = Modifier
        .size(50.dp)
        .clickable { vm.poster.value = poster },
      contentScale = ContentScale.Crop
    )
  }
}

@Composable
fun SelectedPoster(
  poster: Poster?
) {
  var palette by remember { mutableStateOf<Palette?>(null) }

  GlideImage(
    imageModel = poster?.poster!!,
    modifier = Modifier
      .aspectRatio(0.8f),
    circularReveal = CircularReveal(),
    shimmerParams = ShimmerParams(
      baseColor = background800,
      highlightColor = shimmerHighLight
    ),
    bitmapPalette = BitmapPalette {
      palette = it
    }
  )

  ColorPalettes(palette)

  Text(
    text = poster.name,
    style = MaterialTheme.typography.h2,
    textAlign = TextAlign.Center,
    modifier = Modifier.padding(8.dp)
  )

  Text(
    text = poster.description,
    style = MaterialTheme.typography.body1,
    textAlign = TextAlign.Start,
    modifier = Modifier.padding(8.dp)
  )
}

@Composable
fun ColorPalettes(palette: Palette?) {
  Row(
    modifier = Modifier
      .padding(horizontal = 8.dp, vertical = 16.dp)
      .horizontalScroll(rememberScrollState())
  ) {
    Crossfade(
      targetState = palette,
      modifier = Modifier
        .padding(horizontal = 8.dp)
        .size(45.dp)
    ) {
      Box(
        modifier = Modifier
          .background(color = Color(it?.lightVibrantSwatch?.rgb ?: 0))
          .fillMaxSize()
      )
    }
    Crossfade(
      targetState = palette,
      modifier = Modifier
        .padding(horizontal = 8.dp)
        .size(45.dp)
    ) {
      Box(
        modifier = Modifier
          .background(color = Color(it?.lightMutedSwatch?.rgb ?: 0))
          .fillMaxSize()
      )
    }
    Crossfade(
      targetState = palette,
      modifier = Modifier
        .padding(horizontal = 8.dp)
        .size(45.dp)
    ) {
      Box(
        modifier = Modifier
          .background(color = Color(it?.vibrantSwatch?.rgb ?: 0))
          .fillMaxSize()
      )
    }
    Crossfade(
      targetState = palette,
      modifier = Modifier
        .padding(horizontal = 8.dp)
        .size(45.dp)
    ) {
      Box(
        modifier = Modifier
          .background(color = Color(it?.mutedSwatch?.rgb ?: 0))
          .fillMaxSize()
      )
    }
    Crossfade(
      targetState = palette,
      modifier = Modifier
        .padding(horizontal = 8.dp)
        .size(45.dp)
    ) {
      Box(
        modifier = Modifier
          .background(color = Color(it?.darkVibrantSwatch?.rgb ?: 0))
          .fillMaxSize()
      )
    }
    Crossfade(
      targetState = palette,
      modifier = Modifier
        .padding(horizontal = 8.dp)
        .size(45.dp)
    ) {
      Box(
        modifier = Modifier
          .background(color = Color(it?.darkMutedSwatch?.rgb ?: 0))
          .fillMaxSize()
      )
    }
    Crossfade(
      targetState = palette,
      modifier = Modifier
        .padding(horizontal = 8.dp)
        .size(45.dp)
    ) {
      Box(
        modifier = Modifier
          .background(color = Color(it?.dominantSwatch?.rgb ?: 0))
          .fillMaxSize()
      )
    }
  }
}

@Composable
fun HomePoster(
  poster: Poster,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  Surface(
    modifier = modifier
      .padding(4.dp)
      .clickable(
        onClick = { }
      ),
    color = MaterialTheme.colors.onBackground,
    elevation = 8.dp,
    shape = RoundedCornerShape(8.dp)
  ) {
    ConstraintLayout {
      val (image, title, content) = createRefs()
      FrescoImage(
        imageUrl = poster.poster,
        modifier = Modifier
          .aspectRatio(0.8f)
          .constrainAs(image) {
            centerHorizontallyTo(parent)
            top.linkTo(parent.top)
          }
          .clickable {
            Toast
              .makeText(context, poster.name, Toast.LENGTH_SHORT)
              .show()
          },
        circularReveal = CircularReveal(),
        shimmerParams = ShimmerParams(
          baseColor = background800,
          highlightColor = shimmerHighLight
        )
      )
      Text(
        text = poster.name,
        style = MaterialTheme.typography.h2,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .constrainAs(title) {
            centerHorizontallyTo(parent)
            top.linkTo(image.bottom)
          }
          .padding(8.dp)
      )
      Text(
        text = poster.playtime,
        style = MaterialTheme.typography.body1,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .constrainAs(content) {
            centerHorizontallyTo(parent)
            top.linkTo(title.bottom)
          }
          .padding(horizontal = 8.dp)
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
