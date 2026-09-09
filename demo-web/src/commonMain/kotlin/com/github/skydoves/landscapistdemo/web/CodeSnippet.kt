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
package com.github.skydoves.landscapistdemo.web

/**
 * The Kotlin that would produce what is on screen.
 *
 * This is the part of a playground worth keeping: the controls are only interesting if what they
 * describe can be carried back into a real project. Every branch here mirrors one in
 * `PlaygroundScreen`, so the two have to be edited together.
 */
internal fun buildSnippet(
  url: String,
  sizeMode: SizeMode,
  scaleMode: ScaleMode,
  source: LoadSource,
  toggles: PluginToggles,
): String {
  val plugins = buildList {
    if (toggles.crossfade) add("+CrossfadePlugin(duration = 450)")
    if (toggles.circularReveal) add("+CircularRevealPlugin(duration = 600)")
    if (toggles.shimmer) {
      add(
        "+ShimmerPlugin(\n" +
          "      shimmer = Shimmer.Resonate(\n" +
          "        baseColor = Color.DarkGray,\n" +
          "        highlightColor = Color.LightGray,\n" +
          "      ),\n" +
          "    )",
      )
    }
    if (toggles.zoomable) add("+ZoomablePlugin(state = zoomableState)")
    if (toggles.blurHash) {
      add(
        "+BlurHashPlugin(blurHash = \"$SAMPLE_BLUR_HASH\", width = 32, height = 32)",
      )
    }
    if (toggles.thumbHash) add("+ThumbHashPlugin.fromBase64(\"$SAMPLE_THUMB_HASH\")!!")
    if (toggles.thumbnail) add("+ThumbnailPlugin(requestSize = IntSize(20, 20))")
    if (toggles.placeholder) {
      add("+PlaceholderPlugin.Loading(ColorPainter(Color(0xFF3A3A3A)))")
      add("+PlaceholderPlugin.Failure(ColorPainter(Color(0xFF7A1F1F)))")
    }
    if (toggles.progressive) add("+ProgressiveLoadingPlugin()")
  }

  val requestLines = buildList {
    if (source == LoadSource.Network) {
      add("memoryCachePolicy(CachePolicy.DISABLED)")
      add("diskCachePolicy(CachePolicy.DISABLED)")
    }
    if (toggles.progressive) add("progressiveEnabled(true)")
  }

  return buildString {
    append("LandscapistImage(\n")
    append("  imageModel = { \"$url\" },\n")
    append("  modifier = Modifier${sizeMode.modifierSource},\n")
    if (requestLines.isNotEmpty()) {
      append("  requestBuilder = {\n")
      requestLines.forEach { append("    $it\n") }
      append("  },\n")
    }
    if (plugins.isNotEmpty()) {
      append("  component = rememberImageComponent {\n")
      plugins.forEach { append("    $it\n") }
      append("  },\n")
    }
    append("  imageOptions = ImageOptions(contentScale = ContentScale.${scaleMode.label}),\n")
    append("  onImageStateChanged = { state -> /* Loading, Success, Failure */ },\n")
    append(")")
  }
}

/** How each sizing mode reads as a `Modifier` chain in the snippet. */
private val SizeMode.modifierSource: String
  get() = when (this) {
    SizeMode.Fixed -> "\n    .size(200.dp)"
    SizeMode.FillWidth -> "\n    .fillMaxWidth()"
    SizeMode.AspectRatio -> "\n    .fillMaxWidth()\n    .aspectRatio(16f / 9f)"
    SizeMode.Unsized -> ""
  }
