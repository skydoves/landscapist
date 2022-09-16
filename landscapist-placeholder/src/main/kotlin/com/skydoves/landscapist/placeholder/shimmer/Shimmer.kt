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
@file:JvmName("Shimmer")
@file:JvmMultifileClass

package com.skydoves.landscapist.placeholder.shimmer

import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.util.Pools
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Create a shimmering effect composable with base and highlighting colors.
 *
 * @param modifier basic modifier, must be applied fillMaxSize().
 * @param baseColor base background color of this composable.
 * @param highlightColor highlight shimmering effect color of this composable.
 * @param intensity controls the brightness of the highlight at the center.
 * @param dropOff controls the size of the fading edge of the highlight.
 * @param tilt angle at which the highlight is tilted, measured in degrees.
 * @param durationMillis animation duration of the shimmering start to end.
 */
@Composable
public fun Shimmer(
  modifier: Modifier = Modifier,
  baseColor: Color,
  highlightColor: Color,
  shimmerWidth: Dp? = null,
  intensity: Float = DefaultShimmerIntensity,
  dropOff: Float = DefaultShimmerDropOff,
  tilt: Float = DefaultShimmerTilt,
  durationMillis: Int = DefaultDurationMillis
) {
  val shimmerWidthPx = with(LocalDensity.current) { shimmerWidth?.toPx() }
  val animatedProgress = remember { Animatable(0f) }
  LaunchedEffect(key1 = baseColor) {
    animatedProgress.animateTo(
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = durationMillis, easing = LinearEasing)
      )
    )
  }

  Box(modifier) {
    Canvas(Modifier.fillMaxSize()) {
      val paint = paintPool.acquire() ?: Paint()
      val shaderMatrix = Matrix()
      val tiltTan = tan(Math.toRadians(tilt.toDouble()))
      val width = shimmerWidthPx ?: (size.width + tiltTan * size.height).toFloat()

      try {
        val dx = offset(-width, width, animatedProgress.value)
        val shader: Shader = LinearGradientShader(
          from = Offset(0f, 0f),
          to = Offset(size.width, 0f),
          colors = listOf(
            baseColor,
            highlightColor,
            highlightColor,
            baseColor
          ),
          colorStops = listOf(
            max((1f - intensity - dropOff) / 2f, 0f),
            max((1f - intensity - 0.001f) / 2f, 0f),
            min((1f + intensity + 0.001f) / 2f, 1f),
            min((1f + intensity + dropOff) / 2f, 1f)
          ),
          tileMode = TileMode.Clamp
        )
        val brush = ShaderBrush(shader)
        paint.asFrameworkPaint().apply {
          isAntiAlias = true
          xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
          setShader(shader)
        }

        shaderMatrix.reset()
        shaderMatrix.setRotate(tilt, size.width / 2f, size.height / 2f)
        shaderMatrix.postTranslate(dx, 0f)
        shader.setLocalMatrix(shaderMatrix)

        drawIntoCanvas { canvas ->
          canvas.saveLayer(size.toRect(), paint)

          drawRect(brush, Offset(0f, 0f), size)

          canvas.restore()
        }
      } finally {
        // resets the paint and release to the pool.
        paint.asFrameworkPaint().reset()
        paintPool.release(paint)
      }
    }
  }
}

/** returns a shimmer matrix offset. */
private fun offset(start: Float, end: Float, percent: Float): Float {
  return start + (end - start) * percent
}

/** A definition of the default intensity. */
internal const val DefaultShimmerIntensity = 0f

/** A definition of the default dropOff. */
internal const val DefaultShimmerDropOff = 0.5f

/** A definition of the default tilt. */
internal const val DefaultShimmerTilt = 20f

/** A definition of the default duration. */
internal const val DefaultDurationMillis = 650

/** paint pool which caching and reusing [Paint] instances. */
private val paintPool = Pools.SimplePool<Paint>(2)
