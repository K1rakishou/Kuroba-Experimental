package com.github.k1rakishou.chan.features.filters

import android.content.Context
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.consumeClicks
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCardView
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCheckbox
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController
import com.github.k1rakishou.model.data.filter.ChanFilterMutable
import com.github.k1rakishou.model.data.filter.FilterType

class FilterTypeSelectionController(
  context: Context,
  private val chanFilterMutable: ChanFilterMutable,
  private val onSelected: (newFilterType: Int) -> Unit
) : BaseFloatingComposeController(context) {

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @Composable
  override fun BoxScope.BuildContent() {
    KurobaComposeCardView(
      modifier = Modifier
        .widthIn(max = 600.dp)
        .wrapContentHeight()
        .consumeClicks()
        .align(Alignment.Center)
        .verticalScroll(rememberScrollState())
    ) {
      val filterTypes = remember { FilterType.values() }
      val checkedFilterTypes = remember {
        val map = mutableMapOf<FilterType, MutableState<Boolean>>()

        filterTypes.forEach { filterType ->
          map[filterType] = mutableStateOf(chanFilterMutable.type and filterType.flag != 0)
        }

        return@remember map
      }

      Column {
        KurobaComposeText(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(8.dp),
          textAlign = TextAlign.Center,
          text = stringResource(id = R.string.filter_type_selection_controller_title)
        )

        Spacer(modifier = Modifier.height(12.dp))

        filterTypes.forEach { filterType ->
          var currentlyChecked by checkedFilterTypes[filterType]!!
          val filterTypeName = remember { FilterType.filterTypeName(filterType) }

          KurobaComposeCheckbox(
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight(),
            text = filterTypeName,
            currentlyChecked = currentlyChecked,
            onCheckChanged = { checked -> currentlyChecked = checked }
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(top = 6.dp)
        ) {
          Spacer(modifier = Modifier.weight(1f))

          KurobaComposeTextBarButton(
            onClick = { pop() },
            text = stringResource(id = R.string.cancel)
          )

          Spacer(modifier = Modifier.width(24.dp))

          KurobaComposeTextBarButton(
            onClick = {
              onOkClicked(checkedFilterTypes)
              pop()
            },
            text = stringResource(id = R.string.ok)
          )
        }

        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }

  private fun onOkClicked(checkedFilterTypes: Map<FilterType, MutableState<Boolean>>) {
    var newFilterType = 0

    checkedFilterTypes.entries.forEach { (filterType, checked) ->
      if (checked.value) {
        newFilterType = newFilterType or filterType.flag
      }
    }

    onSelected(newFilterType)
  }
}