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
package com.skydoves.landscapist.image

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints

/**
 * Takes the whole of the space offered and keeps the drawing inside it.
 *
 * Both serve the [Modifier.paint] that follows, which sizes to the painter unless given fixed
 * constraints and draws past the edges when the content scale crops. Compose spells this as
 * `fillMaxSize().clipToBounds()`, which is two more nodes per image and a graphics layer for the
 * clip. Clipping while drawing needs no layer.
 *
 * It has to come before the paint in the chain, so the fill decides what the paint is measured
 * against and the clip is still in effect when [ContentDrawScope.drawContent] reaches it.
 *
 * @param ownLayer Whether this image needs a graphics layer of its own. A plain bitmap painter is
 * static and does not.
 */
internal fun Modifier.fillAndClip(ownLayer: Boolean): Modifier = if (ownLayer) {
  // A draw time state read is only observed for a node that owns a layer. Without one it is
  // attributed to the nearest ancestor that has one, so an animating image redraws the whole list.
  this.fillMaxSize().clipToBounds()
} else {
  this.then(FillAndClipElement)
}

private object FillAndClipElement : ModifierNodeElement<FillAndClipNode>() {
  override fun create(): FillAndClipNode = FillAndClipNode()
  override fun update(node: FillAndClipNode): Unit = Unit
  override fun InspectorInfo.inspectableProperties() {
    name = "fillAndClip"
  }
  override fun equals(other: Any?): Boolean = other === this
  override fun hashCode(): Int = "landscapist.fillAndClip".hashCode()
}

private class FillAndClipNode : Modifier.Node(), LayoutModifierNode, DrawModifierNode {

  /**
   * Fixes each bounded axis to its bound and leaves an unbounded one alone, so the paint that
   * follows still sizes that axis from the image.
   */
  override fun MeasureScope.measure(
    measurable: Measurable,
    constraints: Constraints,
  ): MeasureResult {
    val minWidth: Int
    val maxWidth: Int
    if (constraints.hasBoundedWidth) {
      minWidth = constraints.maxWidth
      maxWidth = constraints.maxWidth
    } else {
      minWidth = constraints.minWidth
      maxWidth = constraints.maxWidth
    }
    val minHeight: Int
    val maxHeight: Int
    if (constraints.hasBoundedHeight) {
      minHeight = constraints.maxHeight
      maxHeight = constraints.maxHeight
    } else {
      minHeight = constraints.minHeight
      maxHeight = constraints.maxHeight
    }
    val placeable = measurable.measure(Constraints(minWidth, maxWidth, minHeight, maxHeight))
    return layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
  }

  override fun ContentDrawScope.draw() {
    clipRect { this@draw.drawContent() }
  }
}
