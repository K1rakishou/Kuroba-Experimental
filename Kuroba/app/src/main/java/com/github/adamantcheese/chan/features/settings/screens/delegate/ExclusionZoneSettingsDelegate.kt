package com.github.adamantcheese.chan.features.settings.screens.delegate

import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Build
import android.widget.ArrayAdapter
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.features.gesture_editor.AdjustAndroid10GestureZonesController
import com.github.adamantcheese.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder
import com.github.adamantcheese.chan.features.gesture_editor.AttachSide
import com.github.adamantcheese.chan.features.gesture_editor.ExclusionZone
import com.github.adamantcheese.chan.ui.controller.navigation.NavigationController
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.AndroidUtils.showToast

class ExclusionZoneSettingsDelegate(
  private val context: Context,
  private val navigationController: NavigationController,
  private val exclusionZonesHolder: Android10GesturesExclusionZonesHolder
) {
  private val arrayAdapter = ArrayAdapter<String>(context, android.R.layout.simple_list_item_1)

  init {
    //  !!!!!!!!!!!!!!!!!!!!!!!!!!
    //  When changing the following list don't forget to update indexes in LEFT_ZONES_INDEXES,
    //  RIGHT_ZONES_INDEXES
    //  !!!!!!!!!!!!!!!!!!!!!!!!!!
    arrayAdapter.add(context.getString(R.string.setting_exclusion_zones_left_zone_portrait))
    arrayAdapter.add(context.getString(R.string.setting_exclusion_zones_right_zone_portrait))
    arrayAdapter.add(context.getString(R.string.setting_exclusion_zones_left_zone_landscape))
    arrayAdapter.add(context.getString(R.string.setting_exclusion_zones_right_zone_landscape))
  }

  @Suppress("MoveLambdaOutsideParentheses")
  fun showZonesDialog() {
    if (!AndroidUtils.isAndroid10()) {
      return
    }

    AlertDialog.Builder(context)
      .setTitle(R.string.setting_exclusion_zones_actions_dialog_title)
      .setAdapter(arrayAdapter, { dialog: DialogInterface, selectedIndex: Int ->
        onOptionClicked(selectedIndex)
        dialog.dismiss()
      })
      .create()
      .show()
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun onOptionClicked(selectedIndex: Int) {
    val orientation = when (selectedIndex) {
      in PORTRAIT_ORIENTATION_INDEXES -> Configuration.ORIENTATION_PORTRAIT
      in LANDSCAPE_ORIENTATION_INDEXES -> Configuration.ORIENTATION_LANDSCAPE
      else -> throw IllegalStateException("Unhandled orientation index $selectedIndex")
    }

    if (AndroidUtils.getScreenOrientation() != orientation) {
      showToast(context, R.string.setting_exclusion_zones_wrong_phone_orientation)
      return
    }

    val attachSide = when (selectedIndex) {
      in LEFT_ZONES_INDEXES -> AttachSide.Left
      in RIGHT_ZONES_INDEXES -> AttachSide.Right
      else -> {
        // this will need to be updated if any swipe up/down actions are added to the application
        throw IllegalStateException("Unhandled AttachSide index $selectedIndex")
      }
    }

    if (exclusionZonesHolder.getZoneOrNull(orientation, attachSide) != null) {
      showEditOrRemoveZoneDialog(orientation, attachSide)
      return
    }

    showZoneEditorController(attachSide, null)
  }

  @Suppress("MoveLambdaOutsideParentheses")
  @RequiresApi(Build.VERSION_CODES.Q)
  private fun showEditOrRemoveZoneDialog(orientation: Int, attachSide: AttachSide) {
    AlertDialog.Builder(context)
      .setTitle(R.string.setting_exclusion_zones_edit_or_remove_zone_title)
      .setPositiveButton(R.string.edit, { dialog, _ ->
        val skipZone: ExclusionZone = exclusionZonesHolder.getZoneOrNull(orientation, attachSide)
          ?: throw IllegalStateException("skipZone is null! (orientation = $orientation, attachSide = $attachSide)")

        showZoneEditorController(attachSide, skipZone)
        dialog.dismiss()
      })
      .setNegativeButton(R.string.remove, { dialog, _ ->
        exclusionZonesHolder.removeZone(orientation, attachSide)
        showToast(context, R.string.setting_exclusion_zones_zone_remove_message)
        dialog.dismiss()
      })
      .create()
      .show()
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun showZoneEditorController(attachSide: AttachSide, skipZone: ExclusionZone?) {
    val adjustGestureZonesController = AdjustAndroid10GestureZonesController(context)
    adjustGestureZonesController.setAttachSide(attachSide)
    adjustGestureZonesController.setSkipZone(skipZone)

    navigationController.presentController(adjustGestureZonesController)
  }

  companion object {
    //  !!!!!!!!!!!!!!!!!!!!!!!!!!
    //  When changing the following indexes don't forget to update arrayAdapter
    //  !!!!!!!!!!!!!!!!!!!!!!!!!!
    private val LEFT_ZONES_INDEXES = intArrayOf(0, 2)
    private val RIGHT_ZONES_INDEXES = intArrayOf(1, 3)
    private val PORTRAIT_ORIENTATION_INDEXES = intArrayOf(0, 1)
    private val LANDSCAPE_ORIENTATION_INDEXES = intArrayOf(2, 3)
  }
}