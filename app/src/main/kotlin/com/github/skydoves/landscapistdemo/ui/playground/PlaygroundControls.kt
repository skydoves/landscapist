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

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.skydoves.landscapistdemo.theme.purple200
import kotlin.math.roundToInt

/** A heading that separates one group of controls from the next. */
@Composable
internal fun SectionHeader(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.h2,
    fontSize = 15.sp,
    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 4.dp),
  )
}

/** Small explanatory text under a heading or a control. */
@Composable
internal fun HintText(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.body2,
    fontSize = 11.sp,
    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
  )
}

/**
 * A horizontally scrolling row of buttons, one of which is selected. Scrolling rather than
 * wrapping, so it stays one line tall on a phone.
 */
@Composable
internal fun <T> ChoiceRow(
  options: List<T>,
  selected: T,
  label: (T) -> String,
  onSelected: (T) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 12.dp, vertical = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    options.forEach { option ->
      val isSelected = option == selected
      Button(
        onClick = { onSelected(option) },
        elevation = null,
        colors = ButtonDefaults.buttonColors(
          backgroundColor = if (isSelected) purple200 else Color(0xFF9E9E9E),
          contentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
      ) {
        Text(
          text = label(option),
          fontSize = 12.sp,
          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
      }
    }
  }
}

/** A horizontally scrolling row of buttons that each run an action. */
@Composable
internal fun ActionRow(actions: List<Pair<String, () -> Unit>>) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 12.dp, vertical = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    actions.forEach { (text, action) ->
      Button(
        onClick = action,
        elevation = null,
        colors = ButtonDefaults.buttonColors(
          backgroundColor = purple200,
          contentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
      ) {
        Text(text = text, fontSize = 12.sp)
      }
    }
  }
}

/** A labelled switch, one per plugin. */
@Composable
internal fun ToggleRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 12.dp, end = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.body1,
      fontSize = 13.sp,
      modifier = Modifier.weight(1f),
    )
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(checkedThumbColor = purple200),
    )
  }
}

/** A labelled integer slider, used for the blur radius. */
@Composable
internal fun SliderRow(
  label: String,
  value: Int,
  // Two ints rather than a range: a ClosedFloatingPointRange is unstable to Compose, which makes
  // this composable unskippable and shows up in the stability dump.
  minimum: Int,
  maximum: Int,
  steps: Int,
  onValueChange: (Int) -> Unit,
) {
  Column(modifier = Modifier.padding(horizontal = 12.dp)) {
    Text(
      text = "$label: $value",
      style = MaterialTheme.typography.body1,
      fontSize = 13.sp,
    )
    Slider(
      value = value.toFloat(),
      onValueChange = { onValueChange(it.roundToInt()) },
      valueRange = minimum.toFloat()..maximum.toFloat(),
      steps = steps,
    )
  }
}

/** The state, size, data source and timing of the current load. */
@Composable
internal fun ReadoutPanel(readout: ImageReadout, url: String) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 6.dp),
    backgroundColor = Color(0x22808080),
    elevation = 0.dp,
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      ReadoutLine("state", readout.state)
      ReadoutLine(
        label = "decoded size",
        value = if (readout.width > 0 || readout.height > 0) {
          "${readout.width} x ${readout.height}"
        } else {
          "-"
        },
      )
      ReadoutLine("data source", readout.dataSource)
      ReadoutLine(
        label = "elapsed",
        value = if (readout.elapsedMs >= 0) "${readout.elapsedMs} ms" else "-",
      )
      if (readout.failure != null) {
        ReadoutLine("failure", readout.failure)
      }
      ReadoutLine("url", url)
    }
  }
}

@Composable
private fun ReadoutLine(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = label,
      style = MaterialTheme.typography.body2,
      fontSize = 12.sp,
      modifier = Modifier.weight(0.35f),
    )
    Text(
      text = value,
      style = MaterialTheme.typography.body1,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.weight(0.65f),
    )
  }
}

/** The swatches the palette plugin extracted, so it is visible that it ran. */
@Composable
internal fun PaletteStrip(colors: List<Int>) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    colors.forEach { color ->
      Box(
        modifier = Modifier
          .size(28.dp)
          .background(Color(color)),
      )
    }
  }
}
