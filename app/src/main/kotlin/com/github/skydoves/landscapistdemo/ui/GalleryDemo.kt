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
package com.github.skydoves.landscapistdemo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.skydoves.landscapistdemo.theme.purple200
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.gallery.ImageGallery
import com.skydoves.landscapist.gallery.ImageViewer
import com.skydoves.landscapist.gallery.rememberImageViewerState
import com.skydoves.landscapist.placeholder.shimmer.Shimmer
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin

/**
 * Demo screen showing ImageGallery and ImageViewer in action.
 * Tapping a gallery image opens the full-screen ImageViewer.
 */
@Composable
fun GalleryDemoScreen(paddingValues: PaddingValues) {
  val imageUrls = remember {
    listOf(
      "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600",
      "https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=600",
      "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=600",
      "https://images.unsplash.com/photo-1433086966358-54859d0ed716?w=600",
      "https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=600",
      "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=600",
      "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=600",
      "https://images.unsplash.com/photo-1490730141103-6cac27aaab94?w=600",
      "https://images.unsplash.com/photo-1475924156734-496f6cac6ec1?w=600",
      "https://images.unsplash.com/photo-1472214103451-9374bd1c798e?w=600",
      "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?w=600",
      "https://images.unsplash.com/photo-1504567961542-e24d9439a724?w=600",
    )
  }

  var showViewer by remember { mutableStateOf(false) }
  var selectedPage by remember { mutableIntStateOf(0) }
  var selectedIndices by remember { mutableStateOf(emptySet<Int>()) }

  val component = rememberImageComponent {
    +ShimmerPlugin(
      shimmer = Shimmer.Resonate(
        baseColor = Color.DarkGray,
        highlightColor = Color.LightGray,
      ),
    )
  }

  Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
    ImageGallery(
      images = imageUrls,
      columns = GridCells.Fixed(3),
      contentPadding = PaddingValues(2.dp),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
      component = component,
      imageOptions = ImageOptions(contentScale = ContentScale.Crop),
      selectable = true,
      selectedIndices = selectedIndices,
      onSelectionChanged = { selectedIndices = it },
      onImageClick = { index, _ ->
        selectedPage = index
        showViewer = true
      },
      selectionOverlay = { _, selected ->
        if (selected) {
          Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.TopEnd,
          ) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = purple200,
              modifier = Modifier
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape),
            )
          }
        }
      },
      header = {
        TopAppBar(
          backgroundColor = purple200,
          title = {
            Text(
              text = if (selectedIndices.isEmpty()) {
                "Gallery Demo"
              } else {
                "${selectedIndices.size} selected"
              },
              color = Color.White,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
            )
          },
        )
      },
    )
  }

  // Full-screen image viewer overlay
  if (showViewer) {
    BackHandler { showViewer = false }

    val viewerState = rememberImageViewerState(
      initialPage = selectedPage,
      pageCount = { imageUrls.size },
    )

    ImageViewer(
      images = imageUrls,
      state = viewerState,
      component = component,
      imageOptions = ImageOptions(contentScale = ContentScale.Fit),
      onDismiss = { showViewer = false },
      onImageTap = { /* could toggle UI overlays */ },
      topBar = { currentPage, totalPages ->
        TopAppBar(
          backgroundColor = Color.Black.copy(alpha = 0.5f),
          title = {
            Text(
              text = "${currentPage + 1} / $totalPages",
              color = Color.White,
              fontSize = 16.sp,
            )
          },
        )
      },
    )
  }
}
