// © Art Chaidarun

package com.chaidarun.chronofile

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(initialText: String, onSave: (String) -> Unit, onNavigateUp: () -> Unit) {
  var text by remember { mutableStateOf(initialText) }
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
          IconButton(onClick = onNavigateUp) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
          }
        },
        actions = {
          IconButton(
            onClick = {
              onSave(text)
              Log.i(TAG, "Edited config file")
              onNavigateUp()
            }
          ) {
            Icon(Icons.Filled.Check, contentDescription = "Save")
          }
          IconButton(
            onClick = {
              Log.i(TAG, "Canceled config file edit")
              onNavigateUp()
            }
          ) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = ColorPrimary,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White,
            navigationIconContentColor = Color.White,
          ),
      )
    },
    containerColor = ColorPrimaryDark,
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
    ) {
      val instructions = buildAnnotatedString {
        append(
          "If you'd like to aggregate multiple activity types into a single one for charting" +
            " purposes, you can define such groups in the "
        )
        withLink(LinkAnnotation.Url("https://en.wikipedia.org/wiki/JSON#Syntax")) {
          withStyle(SpanStyle(color = ColorAccent, textDecoration = TextDecoration.Underline)) {
            append("JSON")
          }
        }
        append(
          " config object below. Example:\n\n{\"activityGroups\":{\"Chores\":[\"Cook\"," +
            "\"Laundry\"],\"Exercise\":[\"Football\",\"Yoga\"]}}"
        )
      }
      Text(
        instructions,
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
      )
      BasicTextField(
        value = text,
        onValueChange = { text = it },
        textStyle =
          MaterialTheme.typography.bodyMedium.copy(
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
          ),
        cursorBrush = SolidColor(Color.White),
        modifier =
          Modifier.fillMaxWidth()
            .weight(1f)
            .padding(top = 4.dp)
            .background(ColorPrimary)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
      )
    }
  }
}
