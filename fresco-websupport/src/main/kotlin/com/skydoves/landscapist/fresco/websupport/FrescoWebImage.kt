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
@file:Suppress("unused")
@file:JvmName("FrescoWebImage")
@file:JvmMultifileClass

package com.skydoves.landscapist.fresco.websupport

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import com.facebook.drawee.backends.pipeline.PipelineDraweeControllerBuilder
import com.facebook.drawee.generic.GenericDraweeHierarchy
import com.facebook.drawee.generic.GenericDraweeHierarchyInflater
import com.facebook.drawee.view.DraweeHolder
import com.skydoves.landscapist.rememberDrawablePainter

/**
 * FrescoWebImage requests loading an image using Fresco Pipeline for web supports like webp.
 *
 * ```
 * FrescoWebImage(
 *  controllerBuilder = {
 *   Fresco.newDraweeControllerBuilder()
 *    .setUri("asset:///animatable.webp")
 *    .setAutoPlayAnimations(true)
 *  },
 *  modifier = Modifier
 *    .width(300.dp)
 *     .height(300.dp)
 * )
 * ```
 *
 * or
 *
 * ```
 * FrescoWebImage(
 *  controllerBuilder = {
 *    Fresco.newDraweeControllerBuilder()
 *      .setUri(
 *        "https://user-images.githubusercontent.com/" +
 *          "24237865/75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg"
 *      )
 *  },
 *  modifier = Modifier
 *    .width(300.dp)
 *     .height(300.dp)
 * )
 * ```
 *
 * @param controllerBuilder PipelineDraweeControllerBuilder for requesting image data.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param contentDescription The content description used to provide accessibility to describe the image.
 * @param alignment The alignment parameter used to place the loaded [ImageBitmap] in the image container.
 * @param alpha The alpha parameter used to apply for the image when it is rendered onscreen.
 * @param contentScale The scale parameter used to determine the aspect ratio scaling to be
 * used for the loaded [ImageBitmap].
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param previewPlaceholder Drawable resource ID which will be displayed when this function is ran in preview mode.
 */
@Composable
public fun FrescoWebImage(
  controllerBuilder: () -> PipelineDraweeControllerBuilder,
  modifier: Modifier = Modifier,
  contentDescription: String? = null,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  @DrawableRes previewPlaceholder: Int = 0,
) {
  if (LocalInspectionMode.current && previewPlaceholder != 0) {
    Image(
      modifier = modifier,
      painter = painterResource(id = previewPlaceholder),
      alignment = alignment,
      contentScale = contentScale,
      alpha = alpha,
      colorFilter = colorFilter,
      contentDescription = contentDescription,
    )
    return
  }

  val context = LocalContext.current
  val hierarchy = GenericDraweeHierarchyInflater.inflateBuilder(context, null).build()
  val holder: DraweeHolder<GenericDraweeHierarchy> = DraweeHolder.create(hierarchy, context)
  controllerBuilder().oldController = holder.controller
  holder.controller = controllerBuilder().build()

  val topLevelDrawable = holder.topLevelDrawable
  Box(modifier = modifier) {
    if (topLevelDrawable != null) {
      Image(
        modifier = modifier,
        painter = rememberDrawablePainter(topLevelDrawable),
        contentDescription = contentDescription,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
      )
    }
  }
}
