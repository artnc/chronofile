// © Art Chaidarun

package com.chaidarun.chronofile

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.core.app.NavUtils
import androidx.core.text.HtmlCompat
import com.chaidarun.chronofile.databinding.ActivityEditorBinding

class EditorActivity : BaseActivity() {
  private val binding by viewBinding(ActivityEditorBinding::inflate)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setSupportActionBar(binding.editorToolbar)
    binding.editorInstructions.apply {
      text =
        HtmlCompat.fromHtml(
          "If you'd like to aggregate multiple activity types into a single one for charting purposes, you can define such groups in the <a href=\"https://en.wikipedia.org/wiki/JSON#Syntax\">JSON</a> config object below. Example:<br /><br />{\"activityGroups\":{\"Chores\":[\"Cook\",\"Laundry\"],\"Exercise\":[\"Football\",\"Yoga\"]}}",
          HtmlCompat.FROM_HTML_MODE_LEGACY,
        )
      movementMethod = LinkMovementMethod.getInstance() // https://stackoverflow.com/a/4303608
    }
    binding.editorText.setText(Store.state.config?.serialize() ?: "")
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_editor, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.action_editor_cancel -> Log.i(TAG, "Canceled config file edit")
      R.id.action_editor_save -> {
        Store.dispatch(Action.SetConfigFromText(binding.editorText.text.toString()))
        Log.i(TAG, "Edited config file")
      }
      else -> return super.onOptionsItemSelected(item)
    }
    NavUtils.navigateUpFromSameTask(this)
    return true
  }
}
