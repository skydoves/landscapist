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
package com.github.skydoves.landscapistdemo.ui.playground

import androidx.compose.runtime.Immutable
import androidx.compose.ui.layout.ContentScale

/**
 * Three of them, so a cold cache is one tap away: switching to a url never loaded is the only way
 * to watch a network load happen again.
 */
internal val playgroundImageUrls: List<String> = listOf(
  "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200",
  "https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=1200",
  "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=1200",
)

/** A url that resolves but cannot be decoded as an image, so the failure path is reachable. */
internal const val FAILING_IMAGE_URL: String =
  "https://images.unsplash.com/this-photo-does-not-exist-404.jpg"

/** The sample hash from the BlurHashPlugin documentation. */
internal const val SAMPLE_BLUR_HASH: String = "LEHV6nWB2yk8pyo0adR*.7kCMdnj"

/** The sample hash from the ThumbHashPlugin documentation. */
internal const val SAMPLE_THUMB_HASH: String = "1QcSHQRnh493V4dIh4eXh1h4kJUI"

/** How the image composable is sized, which is what decides the constraints it decodes against. */
internal enum class SizeMode(val label: String) {
  Fixed("Fixed 200dp"),
  FillWidth("Fill width"),
  AspectRatio("16:9"),
  Unsized("No size"),
}

/** The [ContentScale] applied through `ImageOptions`. */
internal enum class ScaleMode(val label: String, val contentScale: ContentScale) {
  Crop("Crop", ContentScale.Crop),
  Fit("Fit", ContentScale.Fit),
  Inside("Inside", ContentScale.Inside),
  None("None", ContentScale.None),
  FillBounds("FillBounds", ContentScale.FillBounds),
}

/**
 * Where the next load is meant to come from. None of these force a data source: they arrange the
 * conditions, and the readout reports what actually happened.
 */
internal enum class LoadSource(val label: String, val hint: String) {
  Network(
    label = "Network",
    hint = "Both caches are disabled on the request, so every reload goes out to the network.",
  ),
  Memory(
    label = "Memory",
    hint = "Caches on. The first load fills them; reload to see the memory hit.",
  ),
  Disk(
    label = "Disk",
    hint = "Memory cache is cleared before each reload, so a previously loaded image comes " +
      "back off disk.",
  ),
  Failure(
    label = "Failure",
    hint = "Loads a url that is not an image, so the failure state and failure plugins show.",
  ),
}

/**
 * Which plugins are attached to the image.
 *
 * Immutable and in one state so it can key the component: `rememberImageComponent` remembers
 * without keys and would otherwise keep the plugin list built on the first composition.
 */
@Immutable
internal data class PluginToggles(
  val crossfade: Boolean = true,
  val circularReveal: Boolean = false,
  val shimmer: Boolean = false,
  val palette: Boolean = false,
  val zoomable: Boolean = false,
  val blur: Boolean = false,
  val blurRadius: Int = 10,
  val blurHash: Boolean = false,
  val thumbHash: Boolean = false,
  val thumbnail: Boolean = false,
  val placeholder: Boolean = false,
  val progressive: Boolean = false,
)

/** What the readout panel shows about the current load. */
@Immutable
internal data class ImageReadout(
  val state: String = "None",
  val width: Int = 0,
  val height: Int = 0,
  val dataSource: String = "-",
  val elapsedMs: Long = -1L,
  val failure: String? = null,
)
