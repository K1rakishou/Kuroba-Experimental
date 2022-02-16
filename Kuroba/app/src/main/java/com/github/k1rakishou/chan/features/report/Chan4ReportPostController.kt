package com.github.k1rakishou.chan.features.report

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.http.report.PostReportData
import com.github.k1rakishou.chan.core.site.http.report.PostReportResult
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder
import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCardView
import com.github.k1rakishou.chan.ui.compose.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.launch
import javax.inject.Inject

class Chan4ReportPostController(
  context: Context,
  private val postDescriptor: PostDescriptor,
  private val onCaptchaRequired: () -> Unit,
  private val onOpenInWebView: () -> Unit
) : BaseFloatingComposeController(context) {

  @Inject
  lateinit var captchaHolder: CaptchaHolder
  @Inject
  lateinit var siteManager: SiteManager

  private val viewModel by lazy { requireComponentActivity().viewModelByKey<Chan4ReportPostControllerViewModel>() }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    viewModel.resetSelectedCategoryId()
  }

  @Composable
  override fun BoxScope.BuildContent() {
    val reportCategoriesAsync by produceState<AsyncData<List<Chan4ReportPostControllerViewModel.ReportCategory>>>(
      initialValue = AsyncData.Loading,
      producer = {
        val result = viewModel.loadReportCategories(postDescriptor)

        value = when (result) {
          is ModularResult.Error -> AsyncData.Error(result.error)
          is ModularResult.Value -> AsyncData.Data(result.value)
        }
      }
    )
    
    KurobaComposeCardView(
      modifier = Modifier
        .widthIn(min = 256.dp)
        .wrapContentHeight()
        .align(Alignment.Center)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
      ) {
        when (reportCategoriesAsync) {
          AsyncData.NotInitialized,
          AsyncData.Loading -> {
            KurobaComposeProgressIndicator(
              modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(all = 16.dp),
            )
          }
          is AsyncData.Error -> {
            KurobaComposeErrorMessage(
              modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(all = 16.dp),
              error = (reportCategoriesAsync as AsyncData.Error).throwable
            )
          }
          is AsyncData.Data -> {
            val reportCategories = (reportCategoriesAsync as AsyncData.Data).data

            BuildReportCategorySelector(
              reportCategories = reportCategories,
              onSelectCategoryClicked = { selectReportCategory(reportCategories) }
            )
          }
        }

        BuildFooter()
      }
    }
  }

  @Composable
  private fun BuildReportCategorySelector(
    reportCategories: List<Chan4ReportPostControllerViewModel.ReportCategory>,
    onSelectCategoryClicked: () -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val selectedCategoryId by viewModel.selectedCategoryId
    val reporting by viewModel.reporting

    val selectedCategory = remember(key1 = selectedCategoryId) {
      if (selectedCategoryId == null) {
        return@remember null
      }

      return@remember reportCategories
        .firstOrNull { reportCategory -> reportCategory.id == selectedCategoryId }
    }

    val paddingModifier = Modifier
      .padding(horizontal = 8.dp, vertical = 16.dp)

    KurobaComposeCardView(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .clickable(enabled = !reporting, onClick = { onSelectCategoryClicked() })
        .then(paddingModifier),
      backgroundColor = chanTheme.backColorSecondaryCompose
    ) {
      val text = if (selectedCategory == null) {
        stringResource(id = R.string.report_controller_select_report_category)
      } else {
        selectedCategory.description
      }

      KurobaComposeText(
        modifier = paddingModifier,
        textAlign = TextAlign.Center,
        text = text
      )
    }
  }

  @Composable
  private fun BuildFooter() {
    val focusManager = LocalFocusManager.current

    Row(
      modifier = Modifier
        .wrapContentHeight()
        .fillMaxWidth()
        .padding(all = 4.dp)
    ) {

      KurobaComposeTextBarButton(
        text = stringResource(id = R.string.report_controller_open_in_webview),
        onClick = {
          pop()
          onOpenInWebView()
        }
      )

      Spacer(modifier = Modifier.weight(1f))

      KurobaComposeTextBarButton(
        text = stringResource(id = R.string.close),
        onClick = {
          focusManager.clearFocus(force = true)
          pop()
        }
      )

      Spacer(modifier = Modifier.width(8.dp))

      val selectedCategoryId by viewModel.selectedCategoryId
      val reporting by viewModel.reporting

      KurobaComposeTextBarButton(
        enabled = selectedCategoryId != null && !reporting,
        text = stringResource(id = R.string.report_controller_report_post),
        onClick = {
          val catId = selectedCategoryId
            ?: return@KurobaComposeTextBarButton

          val isLoggedIn = siteManager.bySiteDescriptor(postDescriptor.siteDescriptor())?.actions()?.isLoggedIn() == true
          val captchaSolution = captchaHolder.solution as? CaptchaSolution.ChallengeWithSolution

          val captchaInfo = when {
            isLoggedIn -> {
              PostReportData.Chan4.CaptchaInfo.UsePasscode
            }
            captchaSolution != null && !captchaSolution.isTokenEmpty() -> {
              PostReportData.Chan4.CaptchaInfo.Solution(captchaSolution)
            }
            else -> {
              onCaptchaRequired()
              return@KurobaComposeTextBarButton
            }
          }

          mainScope.launch {
            val reportPostResult = viewModel.reportPost(
              postDescriptor = postDescriptor,
              captchaInfo = captchaInfo,
              selectedCategoryId = catId
            )
              .toastOnError(longToast = true)
              .valueOrNull()

            when (reportPostResult) {
              PostReportResult.Success -> {
                showToast(
                  getString(R.string.post_reported, postDescriptor.userReadableString()),
                  Toast.LENGTH_LONG
                )

                pop()
              }
              PostReportResult.CaptchaRequired -> {
                onCaptchaRequired()
              }
              is PostReportResult.Error -> {
                showToast(reportPostResult.errorMessage, Toast.LENGTH_LONG)
              }
              PostReportResult.NotSupported -> {
                showToast(R.string.post_report_not_supported, Toast.LENGTH_LONG)
              }
              PostReportResult.AuthRequired,
              null -> {
                // no-op
              }
            }
          }
        }
      )
    }
  }

  private fun selectReportCategory(reportCategories: List<Chan4ReportPostControllerViewModel.ReportCategory>) {
    if (reportCategories.isEmpty()) {
      return
    }

    val items = reportCategories.map { reportCategory ->
      FloatingListMenuItem(key = reportCategory.id, name = reportCategory.description)
    }

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = items,
      itemClickListener = { clickedItem ->
        val clickedReportCategoryId = clickedItem.key as? Int
          ?: return@FloatingListMenuController

        viewModel.updateSelectedCategoryId(clickedReportCategoryId)
      }
    )

    presentController(floatingListMenuController)
  }

}