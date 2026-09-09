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
package com.skydoves.landscapist.zoomable.internal

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent

/**
 * Measures, places and keeps everything under it, and draws none of it.
 *
 * Used where two children stand for the same image and only one of them should be on screen. The
 * other stays composed rather than being removed, so its state survives: a subtree rebuilt when
 * the two swap back would restart whatever its plugins animate.
 */
internal fun Modifier.notDrawn(): Modifier = drawWithContent { }
