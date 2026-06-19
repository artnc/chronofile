// © Art Chaidarun

package com.chaidarun.chronofile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
      modifier
        .toggleable(
          value = checked,
          // No ripple: the checkmark already signals state
          interactionSource = null,
          indication = null,
          role = Role.Checkbox,
          onValueChange = onCheckedChange,
        )
        .padding(vertical = 2.dp),
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
        .padding(vertical = 2.dp)
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

/** Material 3 top-app-bar colors shared by every screen: white content on the ColorPrimary bar */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appTopAppBarColors() =
  TopAppBarDefaults.topAppBarColors(
    actionIconContentColor = Color.White,
    containerColor = ColorPrimary,
    navigationIconContentColor = Color.White,
    titleContentColor = Color.White,
  )

/**
 * Scaffold for the back-arrow screens (Chart, Editor, Earth): a [title], an up-button wired to
 * [onNavigateUp], optional [actions], and the app's dark background
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
  title: String,
  onNavigateUp: () -> Unit,
  actions: @Composable RowScope.() -> Unit = {},
  content: @Composable (PaddingValues) -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(title) },
        navigationIcon = {
          IconButton(onClick = onNavigateUp) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
          }
        },
        actions = actions,
        colors = appTopAppBarColors(),
      )
    },
    containerColor = ColorPrimaryDark,
    content = content,
  )
}
