package com.skydoves.benchmark.landscapist.app

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.components.LocalImageComponent
import com.skydoves.landscapist.image.LandscapistImage

@Composable
fun LandscapistImageProfiles() {
  LandscapistImage(
    modifier = Modifier.size(120.dp),
    imageModel = {
      "https://user-images.githubusercontent.com/" +
        "24237865/75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg"
    },
    component = LocalImageComponent.current,
    imageOptions = ImageOptions(tag = "CoilImage"),
  )
}
