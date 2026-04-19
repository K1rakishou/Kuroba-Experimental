package com.github.k1rakishou.chan.features.bookmarks

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeCard
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeCustomTextField
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.consumeClicks
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyColumnWithFastScroller
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.scaffold.FloatingLazyListScaffoldBuilder
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.base.BaseFloatingComposeController
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.HeaderFloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.model.data.bookmark.BookmarkGroupMatchFlag
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroup
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale

class BookmarkGroupPatternSettingsController(
  context: Context,
  private val bookmarkGroupId: String
) : BaseFloatingComposeController(context) {
  private var matcherValidationTrigger: MutableState<Int> = mutableStateOf(0)
  private val viewModel by viewModelByKey<BookmarkGroupPatternSettingsControllerViewModel>()

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    viewModel.reload(bookmarkGroupId)
  }

  @Composable
  override fun BoxScope.BuildContent() {
    val chanTheme = LocalChanTheme.current

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .align(Alignment.Center)
        .background(chanTheme.backColorCompose)
        .consumeClicks()
    ) {
      val listState = rememberLazyListState()

      with(FloatingLazyListScaffoldBuilder()) {
        Content(
          boxScope = this@BuildContent,
          lazyListState = listState,
          body = { paddings ->
            LazyColumnWithFastScroller(
              state = listState,
              modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(paddings),
              draggableScrollbar = false,
              content = {
                val mutableMatcherFlags = viewModel.mutableMatcherFlags

                items(
                  count = mutableMatcherFlags.size,
                  itemContent = { itemIndex ->
                    val mutableMatcherFlag = mutableMatcherFlags.get(itemIndex)

                    BuildMatcherGroup(
                      modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                      mutableMatcherFlag = mutableMatcherFlag,
                      index = itemIndex,
                      totalCount= mutableMatcherFlags.size,
                      onRemoveMatcherFlagClicked = { index -> onRemoveMatcherFlagClicked(index) },
                      onSelectMatcherFlagClicked = { index -> onSelectMatcherFlagClicked(index) },
                      onSelectMatcherOperatorClicked = { index -> onSelectMatcherOperatorClicked(index) },
                      onAddNewMatcherGroupClicked = { onAddNewMatcherGroupClicked() }
                    )
                  }
                )
              }
            )
          },
          footer = {
            BuildFooter(
              modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
            )
          }
        )
      }
    }
  }

  @Composable
  private fun BuildFooter(modifier: Modifier) {
    val validationTrigger by remember { matcherValidationTrigger }
    val mutableMatcherFlags = viewModel.mutableMatcherFlags

    val validationResult by produceState<GroupMatcherValidationResult>(
      initialValue = GroupMatcherValidationResult.Validating,
      key1 = validationTrigger,
      producer = {
        value = viewModel.validateGroupMatchers(mutableMatcherFlags)
      }
    )

    LaunchedEffect(key1 = true, block = { triggerMatcherValidation() })

    Column(
      modifier = modifier
    ) {
      val text = when (val vr = validationResult) {
        is GroupMatcherValidationResult.Error -> "Error: ${vr.message}"
        GroupMatcherValidationResult.Ok -> "Everything seems to be OK"
        GroupMatcherValidationResult.Validating -> "Validating"
      }

      val bgColor = when (validationResult) {
        is GroupMatcherValidationResult.Error -> validationErrorColor
        GroupMatcherValidationResult.Ok -> validationOkColor
        GroupMatcherValidationResult.Validating -> validationInProgressColor
      }

      Spacer(modifier = Modifier.height(8.dp))

      KurobaComposeText(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .background(bgColor),
        color = Color.White,
        text = text
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
      ) {
        Spacer(modifier = Modifier.weight(1f))

        KurobaComposeTextBarButton(
          onClick = {
            pop()
          },
          text = stringResource(id = R.string.cancel)
        )

        Spacer(modifier = Modifier.width(8.dp))

        KurobaComposeTextBarButton(
          enabled = validationResult.isOk(),
          onClick = {
            controllerScope.launch {
              viewModel.saveGroupMatcherPattern(bookmarkGroupId)
              pop()
            }
          },
          text = stringResource(id = R.string.save)
        )
      }
    }
  }

  @Composable
  private fun BuildMatcherGroup(
    modifier: Modifier,
    mutableMatcherFlag: MatchFlagMutable,
    index: Int,
    totalCount: Int,
    onRemoveMatcherFlagClicked: (Int) -> Unit,
    onSelectMatcherFlagClicked: (Int) -> Unit,
    onSelectMatcherOperatorClicked: (Int) -> Unit,
    onAddNewMatcherGroupClicked: () -> Unit
  ) {
    val chanTheme = LocalChanTheme.current

    val onRemoveMatcherFlagClickedRemembered = rememberUpdatedState(newValue = onRemoveMatcherFlagClicked)
    val onSelectMatcherFlagClickedRemembered = rememberUpdatedState(newValue = onSelectMatcherFlagClicked)
    val onSelectMatcherOperatorClickedRemembered = rememberUpdatedState(newValue = onSelectMatcherOperatorClicked)
    val onAddNewMatcherGroupClickedRemembered = rememberUpdatedState(newValue = onAddNewMatcherGroupClicked)

    val matcherType by mutableMatcherFlag.matcherType
    var patternRaw by mutableMatcherFlag.patternRaw

    val flagAsString = when (matcherType) {
      BookmarkGroupMatchFlag.Type.SiteName -> {
        stringResource(id = R.string.bookmark_group_settings_site_name_matcher)
      }
      BookmarkGroupMatchFlag.Type.BoardCode -> {
        stringResource(id = R.string.bookmark_group_settings_board_code_matcher)
      }
      BookmarkGroupMatchFlag.Type.PostSubject -> {
        stringResource(id = R.string.bookmark_group_settings_post_subject_matcher)
      }
      BookmarkGroupMatchFlag.Type.PostComment -> {
        stringResource(id = R.string.bookmark_group_settings_post_comment_matcher)
      }
      else -> {
        stringResource(id = R.string.bookmark_group_settings_unknown_matcher, matcherType)
      }
    }

    Column(
      modifier = modifier
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
      ) {

        KurobaComposeText(
          modifier = Modifier
            .align(Alignment.CenterVertically)
            .width(20.dp),
          text = "#${index}"
        )

        if (totalCount > 1) {
          KurobaComposeIcon(
            modifier = Modifier
              .size(28.dp)
              .align(Alignment.CenterVertically)
              .kurobaClickable(
                bounded = false,
                onClick = { onRemoveMatcherFlagClickedRemembered.value.invoke(index) }
              ),
            drawableId = R.drawable.ic_clear_white_24dp
          )

          Spacer(modifier = Modifier.width(8.dp))
        } else {
          Spacer(modifier = Modifier.size(36.dp))
        }

        KurobaComposeCard(
          modifier = Modifier
            .wrapContentSize()
            .kurobaClickable(
              bounded = true,
              onClick = { onSelectMatcherFlagClickedRemembered.value.invoke(index) }
            ),
          backgroundColor = chanTheme.backColorSecondaryCompose
        ) {
          Box(modifier = Modifier.weight(1f)) {
            KurobaComposeText(
              modifier = Modifier
                .align(Alignment.Center)
                .padding(4.dp)
                .width(96.dp),
              text = flagAsString
            )
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        KurobaComposeCustomTextField(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
          keyboardOptions = KeyboardOptions(
            autoCorrect = false,
            imeAction = ImeAction.Next,
            keyboardType = KeyboardType.Password
          ),
          labelText = stringResource(id = R.string.bookmark_group_settings_enter_matcher_pattern),
          parentBackgroundColor = chanTheme.backColorCompose,
          value = patternRaw,
          onValueChange = { newValue ->
            patternRaw = newValue
            triggerMatcherValidation()
          }
        )
      }

      val matcherOperator by mutableMatcherFlag.matcherOperator
      Spacer(modifier = Modifier.height(10.dp))

      if (matcherOperator == null && index < ThreadBookmarkGroup.MAX_MATCH_GROUPS) {
        KurobaComposeCard(
          Modifier
            .wrapContentSize()
            .align(Alignment.CenterHorizontally)
            .kurobaClickable(
              bounded = true,
              onClick = { onAddNewMatcherGroupClickedRemembered.value.invoke() }
            ),
          backgroundColor = chanTheme.backColorSecondaryCompose
        ) {
          KurobaComposeText(
            modifier = Modifier
              .weight(1f)
              .padding(horizontal = 8.dp, vertical = 4.dp),
            text = stringResource(id = R.string.bookmark_group_settings_add_matcher)
          )
        }
      } else if (matcherOperator != null) {
        KurobaComposeCard(
          Modifier
            .wrapContentSize()
            .align(Alignment.CenterHorizontally)
            .kurobaClickable(
              bounded = true,
              onClick = { onSelectMatcherOperatorClickedRemembered.value.invoke(index) }
            ),
          backgroundColor = chanTheme.backColorSecondaryCompose
        ) {
          KurobaComposeText(
            modifier = Modifier
              .weight(1f)
              .padding(horizontal = 8.dp, vertical = 4.dp),
            text = matcherOperator!!.name.uppercase(Locale.ENGLISH)
          )
        }
      }
    }
  }

  private fun onSelectMatcherOperatorClicked(index: Int) {
    controllerScope.launch {
      val matcherOperator = selectMatcherConjunctiveOperator()
        ?: return@launch

      viewModel.updateMatcherOperator(index, matcherOperator)
      triggerMatcherValidation()
    }
  }

  private fun onSelectMatcherFlagClicked(index: Int) {
    controllerScope.launch {
      val matcherFlag = selectMatcherFlag()
        ?: return@launch

      viewModel.updateMatcherFlag(index, matcherFlag)
      triggerMatcherValidation()
    }
  }

  private fun onAddNewMatcherGroupClicked() {
    controllerScope.launch {
      val matcherOperator = selectMatcherConjunctiveOperator()
        ?: return@launch

      viewModel.addNextMatcherGroup(matcherOperator)
      triggerMatcherValidation()
    }
  }

  private fun onRemoveMatcherFlagClicked(index: Int) {
    viewModel.removeMatcherFlag(index)
    triggerMatcherValidation()
  }

  private suspend fun selectMatcherFlag(): BookmarkGroupMatchFlag.Type? {
    return suspendCancellableCoroutine<BookmarkGroupMatchFlag.Type?> { cancellableContinuation ->
      val items = mutableListOf<FloatingListMenuItem>()

      items += HeaderFloatingListMenuItem(
        FLAG_ITEM_HEADER,
        getString(R.string.bookmark_group_settings_select_matcher)
      )
      items += FloatingListMenuItem(
        FLAG_ITEM_SITE_NAME,
        getString(R.string.bookmark_group_settings_matcher_site_name)
      )
      items += FloatingListMenuItem(
        FLAG_ITEM_BOARD_CODE,
        getString(R.string.bookmark_group_settings_matcher_board_code)
      )
      items += FloatingListMenuItem(
        FLAG_ITEM_POST_SUBJECT,
        getString(R.string.bookmark_group_settings_matcher_post_subject)
      )
      items += FloatingListMenuItem(
        FLAG_ITEM_POST_COMMENT,
        getString(R.string.bookmark_group_settings_matcher_post_comment)
      )

      val controller = FloatingListMenuController(
        context = context,
        items = items,
        menuDismissListener = { cancellableContinuation.resumeValueSafe(null) },
        itemClickListener = { item ->
          when (item.key as Int) {
            FLAG_ITEM_SITE_NAME -> {
              cancellableContinuation.resumeValueSafe(BookmarkGroupMatchFlag.Type.SiteName)
            }
            FLAG_ITEM_BOARD_CODE -> {
              cancellableContinuation.resumeValueSafe(BookmarkGroupMatchFlag.Type.BoardCode)
            }
            FLAG_ITEM_POST_SUBJECT -> {
              cancellableContinuation.resumeValueSafe(BookmarkGroupMatchFlag.Type.PostSubject)
            }
            FLAG_ITEM_POST_COMMENT -> {
              cancellableContinuation.resumeValueSafe(BookmarkGroupMatchFlag.Type.PostComment)
            }
            else -> {
              cancellableContinuation.resumeValueSafe(null)
            }
          }
        }
      )

      presentController(controller)

      cancellableContinuation.invokeOnCancellation { error ->
        if (error == null) {
          return@invokeOnCancellation
        }

        controller.stopPresenting()
      }
    }
  }

  private suspend fun selectMatcherConjunctiveOperator(): BookmarkGroupMatchFlag.Operator? {
    return suspendCancellableCoroutine<BookmarkGroupMatchFlag.Operator?> { cancellableContinuation ->
      val items = mutableListOf<FloatingListMenuItem>()

      items += HeaderFloatingListMenuItem(
        CONJ_OPERATOR_ITEM_HEADER,
        getString(R.string.bookmark_group_settings_select_matcher_operator)
      )
      items += FloatingListMenuItem(
        CONJ_OPERATOR_ITEM_AND,
        getString(R.string.bookmark_group_settings_matcher_operator_and)
      )
      items += FloatingListMenuItem(
        CONJ_OPERATOR_ITEM_OR,
        getString(R.string.bookmark_group_settings_matcher_operator_or)
      )

      val controller = FloatingListMenuController(
        context = context,
        items = items,
        menuDismissListener = { cancellableContinuation.resumeValueSafe(null) },
        itemClickListener = { item ->
          when (item.key as Int) {
            CONJ_OPERATOR_ITEM_AND -> {
              cancellableContinuation.resumeValueSafe(BookmarkGroupMatchFlag.Operator.And)
            }
            CONJ_OPERATOR_ITEM_OR -> {
              cancellableContinuation.resumeValueSafe(BookmarkGroupMatchFlag.Operator.Or)
            }
            else -> {
              cancellableContinuation.resumeValueSafe(null)
            }
          }
        }
      )

      presentController(controller)

      cancellableContinuation.invokeOnCancellation { error ->
        if (error == null) {
          return@invokeOnCancellation
        }

        controller.stopPresenting()
      }
    }
  }

  private fun triggerMatcherValidation() {
    matcherValidationTrigger.value = matcherValidationTrigger.value + 1
  }

  companion object {
    private const val CONJ_OPERATOR_ITEM_HEADER = 0
    private const val CONJ_OPERATOR_ITEM_AND = 1
    private const val CONJ_OPERATOR_ITEM_OR = 2

    private const val FLAG_ITEM_HEADER = 100
    private const val FLAG_ITEM_SITE_NAME = 101
    private const val FLAG_ITEM_BOARD_CODE = 102
    private const val FLAG_ITEM_POST_SUBJECT = 103
    private const val FLAG_ITEM_POST_COMMENT = 104

    private val validationOkColor = Color(0xFF1cb01c)
    private val validationErrorColor = Color(0xFFad1f1f)
    private val validationInProgressColor = Color(0xFF525050)
  }

}