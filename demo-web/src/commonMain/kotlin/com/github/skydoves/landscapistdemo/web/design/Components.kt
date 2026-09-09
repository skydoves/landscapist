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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/** A bordered card. Every group of controls on the page sits in one. */
@Composable
internal fun Panel(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(DemoColors.Surface)
      .border(1.dp, DemoColors.Border, RoundedCornerShape(14.dp)),
  ) {
    content()
  }
}

/** An all caps heading, used once per group of controls. */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text.uppercase(),
    style = DemoType.Section,
    color = DemoColors.TextFaint,
    modifier = modifier,
  )
}

/** Explanatory text under a control. */
@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier) {
  Text(text = text, style = DemoType.Hint, color = DemoColors.TextMuted, modifier = modifier)
}

/**
 * A pill of mutually exclusive options, in a track that scrolls sideways when it has to.
 *
 * Scrolling rather than wrapping, because the options in one of these are a single axis of choice
 * and reading them on one line is what makes that obvious.
 */
@Composable
internal fun <T> Segmented(
  options: List<T>,
  selected: T,
  label: (T) -> String,
  onSelected: (T) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
      .background(DemoColors.SurfaceSunken)
      .border(1.dp, DemoColors.Border, RoundedCornerShape(10.dp))
      .horizontalScroll(rememberScrollState())
      .padding(3.dp),
    horizontalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    options.forEach { option ->
      val isSelected = option == selected
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(7.dp))
          .background(if (isSelected) DemoColors.Accent else Color.Transparent)
          .clickable(
            interactionSource = remembered(),
            indication = null,
            onClick = { onSelected(option) },
          )
          .padding(horizontal = 12.dp, vertical = 7.dp),
      ) {
        Text(
          text = label(option),
          style = DemoType.Label,
          color = if (isSelected) Color.White else DemoColors.TextMuted,
        )
      }
    }
  }
}

/** A toggle. Reads as on or off from the fill alone, so it needs no separate switch. */
@Composable
internal fun Chip(
  label: String,
  selected: Boolean,
  modifier: Modifier = Modifier,
  onToggle: (Boolean) -> Unit,
) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(9.dp))
      .background(if (selected) DemoColors.AccentSunken else DemoColors.SurfaceSunken)
      .border(
        width = 1.dp,
        color = if (selected) DemoColors.AccentBorder else DemoColors.Border,
        shape = RoundedCornerShape(9.dp),
      )
      .clickable(
        interactionSource = remembered(),
        indication = null,
        onClick = { onToggle(!selected) },
      )
      .padding(horizontal = 10.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    Box(
      modifier = Modifier
        .size(7.dp)
        .clip(CircleShape)
        .background(if (selected) DemoColors.Accent else DemoColors.BorderStrong),
    )
    Text(
      text = label,
      style = DemoType.Label,
      color = if (selected) DemoColors.Text else DemoColors.TextMuted,
    )
  }
}

/** Chips that wrap onto as many lines as they need. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(7.dp),
    verticalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    content()
  }
}

/** A button that runs an action. Ghost by default, filled when it is the obvious next thing. */
@Composable
internal fun ActionButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  primary: Boolean = false,
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(9.dp))
      .background(if (primary) DemoColors.Accent else DemoColors.SurfaceSunken)
      .border(
        width = 1.dp,
        color = if (primary) DemoColors.Accent else DemoColors.Border,
        shape = RoundedCornerShape(9.dp),
      )
      .clickable(interactionSource = remembered(), indication = null, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 8.dp),
  ) {
    Text(
      text = label,
      style = DemoType.Label,
      color = if (primary) Color.White else DemoColors.TextMuted,
    )
  }
}

/** One line of the readout: a faint label on the left, the value in monospace on the right. */
@Composable
internal fun StatRow(label: String, value: String, valueColor: Color = DemoColors.Text) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Text(
      text = label,
      style = DemoType.Hint,
      color = DemoColors.TextFaint,
      modifier = Modifier.width(96.dp),
    )
    Text(
      text = value,
      style = DemoType.Mono,
      color = valueColor,
      modifier = Modifier.weight(1f),
    )
  }
}

/** A coloured dot and a word, for the load state. */
@Composable
internal fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(999.dp))
      .background(DemoColors.SurfaceSunken)
      .border(1.dp, DemoColors.Border, RoundedCornerShape(999.dp))
      .padding(horizontal = 9.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
    Text(text = text, style = DemoType.MonoStrong, color = color)
  }
}

/** Underlined accent text that opens a url. */
@Composable
internal fun LinkText(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  color: Color = DemoColors.TextMuted,
) {
  Text(
    text = label,
    style = DemoType.Label,
    color = color,
    textDecoration = TextDecoration.Underline,
    modifier = modifier.clickable(
      interactionSource = remembered(),
      indication = null,
      onClick = onClick,
    ),
  )
}

/** A hairline across the panel. */
@Composable
internal fun Divider(modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxWidth().height(1.dp).background(DemoColors.Border))
}

@Composable
internal fun VSpace(height: Int) {
  Spacer(modifier = Modifier.height(height.dp))
}

/**
 * Every clickable here suppresses the ripple, so each needs an interaction source it does not
 * otherwise use. Named rather than repeated so the call sites stay about the control.
 */
@Composable
private fun remembered(): MutableInteractionSource =
  androidx.compose.runtime.remember { MutableInteractionSource() }
