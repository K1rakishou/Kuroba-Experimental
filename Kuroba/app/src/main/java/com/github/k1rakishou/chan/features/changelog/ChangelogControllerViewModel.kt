package com.github.k1rakishou.chan.features.changelog

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.core.base.viewmodel.KurobaViewModel
import com.github.k1rakishou.chan.core.compose.AsyncUiData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.usecase.LoadChangelogUseCase
import com.github.k1rakishou.common.ModularResult
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChangelogControllerViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val loadChangelogUseCase: LoadChangelogUseCase
) : KurobaViewModel() {
  private val _changelog = mutableStateOf<AsyncUiData<AnnotatedString>>(AsyncUiData.NotInitialized)
  val changelog: State<AsyncUiData<AnnotatedString>>
    get() = _changelog

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    viewModelScope.launch { fetchChangelog() }
  }

  private suspend fun fetchChangelog() {
    _changelog.value = AsyncUiData.Loading

    val changelogResult = loadChangelogUseCase.execute(
      parameter = LoadChangelogUseCase.Params(
        versionCode = BuildConfig.VERSION_CODE.toLong()
      )
    )

    when (changelogResult) {
      is ModularResult.Error<*> -> {
        _changelog.value = AsyncUiData.Error(changelogResult.error)
      }
      is ModularResult.Value<String> -> {
        val responseHtml = changelogResult.value.replace("\n", "<br>")
        _changelog.value = AsyncUiData.UiData(AnnotatedString.fromHtml(responseHtml))
      }
    }
  }

  class ViewModelFactory @Inject constructor(
    private val loadChangelogUseCase: LoadChangelogUseCase
  ) : ViewModelAssistedFactory<ChangelogControllerViewModel> {
    override fun create(handle: SavedStateHandle): ChangelogControllerViewModel {
      return ChangelogControllerViewModel(
        savedStateHandle = handle,
        loadChangelogUseCase = loadChangelogUseCase
      )
    }
  }
}