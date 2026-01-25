package com.github.k1rakishou.chan.core.di.module.application

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.di.scope.PerActivity
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.preprocessor.SitePreprocessing
import com.github.k1rakishou.chan.core.site.preprocessor.SitePreprocessingEventHandler
import com.github.k1rakishou.chan.core.site.preprocessor.SitePreprocessingEventQueue
import com.github.k1rakishou.chan.ui.captcha.lynxchan.chan8moe.Chan8MoePreprocessor
import com.github.k1rakishou.core_logger.Logger
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
class PreprocessorModule {

  @Provides
  @IntoSet
  fun provideChan8MoePreprocessor(
    okHttpClient: ProxiedOkHttpClient,
    sitePreprocessingEventQueue: SitePreprocessingEventQueue
  ): SitePreprocessing.Preprocessor {
    Logger.deps("Chan8MoePreprocessor")
    return Chan8MoePreprocessor(
      okHttpClient = okHttpClient,
      sitePreprocessingEventQueue = sitePreprocessingEventQueue
    )
  }

  @PerActivity
  @Provides
  fun provideSitePreprocessingEventHandler(dialogFactory: DialogFactory): SitePreprocessingEventHandler {
    Logger.deps("SitePreprocessingEventHandler")
    return SitePreprocessingEventHandler(dialogFactory)
  }

  @Provides
  @Singleton
  fun provideSitePreprocessingEventQueue(): SitePreprocessingEventQueue {
    Logger.deps("SitePreprocessingEventQueue")
    return SitePreprocessingEventQueue()
  }

  @Singleton
  @Provides
  fun provideSitePreprocessManager(
    appScope: CoroutineScope,
    siteManager: SiteManager,
    preprocessors: Set<@JvmSuppressWildcards SitePreprocessing.Preprocessor>
  ): SitePreprocessing {
    Logger.deps("SitePreprocessor")
    return SitePreprocessing(
      appScope = appScope,
      siteManager = siteManager,
      preprocessors = preprocessors
    )
  }
}