package com.github.k1rakishou.chan.ui.compose.post.state

import androidx.compose.runtime.Stable
import com.github.k1rakishou.chan.core.manager.RevealedTextSpoilersManager
import com.github.k1rakishou.chan.core.parser.repository.ParsedPostDataRepository
import com.github.k1rakishou.chan.ui.config.UiConfiguration
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.common.KurobaDispatchers
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlinx.coroutines.CoroutineScope

@Stable
interface ThreadCellStateDependencies {
  val coroutineScope: CoroutineScope
  val appResources: AppResources
  val kurobaDispatchers: KurobaDispatchers
  val uiConfiguration: UiConfiguration
  val themeEngine: ThemeEngine
}

@Stable
class ThreadCellStateDependenciesImpl(
  override val coroutineScope: CoroutineScope,
  override val appResources: AppResources = appDependencies().appResources,
  override val kurobaDispatchers: KurobaDispatchers = appDependencies().kurobaDispatchers,
  override val uiConfiguration: UiConfiguration = appDependencies().uiConfiguration,
  override val themeEngine: ThemeEngine = appDependencies().themeEngine
) : ThreadCellStateDependencies

@Stable
interface PostCellStateDependencies {
  val coroutineScope: CoroutineScope
  val appResources: AppResources
  val parsedPostDataRepository: ParsedPostDataRepository
  val revealedTextSpoilersManager: RevealedTextSpoilersManager
}

@Stable
class PostCellStateDependenciesImpl(
  override val coroutineScope: CoroutineScope,
  override val appResources: AppResources = appDependencies().appResources,
  override val parsedPostDataRepository: ParsedPostDataRepository = appDependencies().parsedPostDataRepository,
  override val revealedTextSpoilersManager: RevealedTextSpoilersManager = appDependencies().revealedTextSpoilersManager,
) : PostCellStateDependencies
