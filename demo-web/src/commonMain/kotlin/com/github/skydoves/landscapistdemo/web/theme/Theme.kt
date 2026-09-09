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
package com.github.skydoves.landscapistdemo.web.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val purple200: Color = Color(0xFF651FFF)
internal val purple500: Color = Color(0xFF6200EA)
internal val background: Color = Color(0xFF2B292B)
internal val background800: Color = Color(0xFF424242)
internal val white87: Color = Color(0xDDFFFFFF)

private val DarkColorPalette = darkColors(
  background = background,
  onBackground = background800,
  primary = purple200,
  primaryVariant = purple500,
  secondary = purple500,
  onPrimary = Color.White,
  onSecondary = Color.White,
)

private val DarkTypography = Typography(
  h1 = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    color = Color.White,
    fontSize = 28.sp,
  ),
  h2 = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    color = Color.White,
    fontSize = 21.sp,
  ),
  body1 = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    color = Color.White,
    fontSize = 14.sp,
  ),
  body2 = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    color = white87,
    fontSize = 14.sp,
  ),
)

private val shapes = Shapes(
  small = RoundedCornerShape(4.dp),
  medium = RoundedCornerShape(4.dp),
  large = RoundedCornerShape(0.dp),
)

/**
 * Dark only, unlike the Android sample's theme.
 *
 * The demo is embedded under the documentation site, which is itself dark, and a playground that
 * changed palette with the visitor's system setting would not match the page around it.
 */
@Composable
internal fun LandscapistDemoTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colors = DarkColorPalette,
    typography = DarkTypography,
    shapes = shapes,
    content = content,
  )
}
