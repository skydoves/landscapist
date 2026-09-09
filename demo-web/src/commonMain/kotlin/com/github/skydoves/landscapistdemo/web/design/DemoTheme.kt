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
package com.github.skydoves.landscapistdemo.web.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The demo's own palette and type scale.
 *
 * Deliberately not a `MaterialTheme`. The page is a tool rather than an app, its controls are all
 * hand rolled, and the only Material component left in the module is `Text`, so a theme would only
 * be supplying defaults that every call site overrides anyway.
 */
internal object DemoColors {
  /** The page itself. */
  val Background: Color = Color(0xFF0D0D12)

  /** Panels sitting on the page. */
  val Surface: Color = Color(0xFF16161D)

  /** Anything sitting on a panel: the code block, an unselected segment's track. */
  val SurfaceSunken: Color = Color(0xFF101016)

  /** Hairlines. Everything is separated by one of these rather than by a shadow. */
  val Border: Color = Color(0xFF262631)
  val BorderStrong: Color = Color(0xFF343443)

  val Text: Color = Color(0xFFE9E9F2)
  val TextMuted: Color = Color(0xFF9494A6)
  val TextFaint: Color = Color(0xFF6C6C7D)

  /** Landscapist's purple, carried over from the Android sample. */
  val Accent: Color = Color(0xFF7C5CFF)
  val AccentSunken: Color = Color(0x287C5CFF)
  val AccentBorder: Color = Color(0x807C5CFF)

  val Success: Color = Color(0xFF3DD68C)
  val Danger: Color = Color(0xFFFF6B6B)
  val Pending: Color = Color(0xFFFFC46B)

  /** The two squares of the checkerboard behind the preview. */
  val CheckerLight: Color = Color(0xFF1C1C24)
  val CheckerDark: Color = Color(0xFF15151B)
}

internal object DemoType {
  val Wordmark: TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 17.sp,
    letterSpacing = (-0.2).sp,
  )

  val Title: TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
    letterSpacing = (-0.5).sp,
  )

  val Section: TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    letterSpacing = 1.2.sp,
  )

  val Body: TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 19.sp,
  )

  val Hint: TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 18.sp,
  )

  val Label: TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 12.5.sp,
  )

  val Mono: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 19.sp,
  )

  val MonoStrong: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
  )
}
