// © Art Chaidarun

package com.chaidarun.chronofile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

// App-themed Material 3 selection controls (green accent on the dark background), shared across
// screens so the checkbox and radio styling stays in one place

@Composable
fun AppCheckbox(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier.toggleable(
        value = checked,
        // No ripple: the checkmark already signals state
        interactionSource = null,
        indication = null,
        role = Role.Checkbox,
        onValueChange = onCheckedChange,
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(
      checked = checked,
      onCheckedChange = null,
      colors =
        CheckboxDefaults.colors(
          checkedColor = ColorAccent,
          uncheckedColor = Color.White,
          checkmarkColor = ColorPrimaryDark,
        ),
    )
    Spacer(Modifier.width(4.dp))
    Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
fun AppRadio(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
  Row(
    // selectable stays outermost so the caller's padding lands inside the clickable region
    modifier =
      Modifier.selectable(
          selected = selected,
          // No ripple: the radio dot already signals selection
          interactionSource = null,
          indication = null,
          role = Role.RadioButton,
          onClick = onClick,
        )
        .then(modifier),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(
      selected = selected,
      onClick = null,
      colors =
        RadioButtonDefaults.colors(selectedColor = ColorAccent, unselectedColor = Color.White),
    )
    Spacer(Modifier.width(4.dp))
    Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
  }
}
