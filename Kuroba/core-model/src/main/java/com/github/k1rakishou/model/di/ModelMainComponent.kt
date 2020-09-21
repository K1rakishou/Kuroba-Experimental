package com.github.k1rakishou.model.di

import android.app.Application
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.model.di.annotation.*
import com.github.k1rakishou.model.repository.*
import com.google.gson.Gson
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.CoroutineScope
import okhttp3.Dns
import javax.inject.Singleton

@Singleton
@Component(
  modules = [
    NetworkModule::class,
    ModelMainModule::class
  ]
)
interface ModelMainComponent {
  fun inject(application: Application)

  fun getGson(): Gson
  fun getMediaServiceLinkExtraContentRepository(): MediaServiceLinkExtraContentRepository
  fun getSeenPostRepository(): SeenPostRepository
  fun getInlinedFileInfoRepository(): InlinedFileInfoRepository
  fun getChanPostRepository(): ChanPostRepository
  fun getHistoryNavigationRepository(): HistoryNavigationRepository
  fun getBookmarksRepository(): BookmarksRepository
  fun getChanThreadViewableInfoRepository(): ChanThreadViewableInfoRepository
  fun getSiteRepository(): SiteRepository
  fun getBoardRepository(): BoardRepository
  fun getChanSavedReplyRepository(): ChanSavedReplyRepository
  fun getChanPostHideRepository(): ChanPostHideRepository
  fun getChanFilterRepository(): ChanFilterRepository

  @Component.Builder
  interface Builder {
    @BindsInstance
    fun application(application: Application): Builder

    @BindsInstance
    fun loggerTagPrefix(@LoggerTagPrefix loggerTagPrefix: String): Builder

    @BindsInstance
    fun verboseLogs(@VerboseLogs verboseLogs: Boolean): Builder

    @BindsInstance
    fun isDevFlavor(@IsDevFlavor isDevFlavor: Boolean): Builder

    @BindsInstance
    fun okHttpDns(@OkHttpDns dns: Dns): Builder

    @BindsInstance
    fun okHttpProtocols(@OkHttpProtocols okHttpProtocols: NetworkModule.OkHttpProtocolList): Builder

    @BindsInstance
    fun appConstants(appConstants: AppConstants): Builder

    @BindsInstance
    fun appCoroutineScope(@AppCoroutineScope scope: CoroutineScope): Builder

    @BindsInstance
    fun betaOrDevBuild(@BetaOrDevBuild betaOrDev: Boolean): Builder

    fun build(): ModelMainComponent
  }

}