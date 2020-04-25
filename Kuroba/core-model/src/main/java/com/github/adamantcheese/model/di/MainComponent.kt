package com.github.adamantcheese.model.di

import android.app.Application
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.model.di.annotation.LoggerTagPrefix
import com.github.adamantcheese.model.di.annotation.OkHttpDns
import com.github.adamantcheese.model.di.annotation.OkHttpProtocols
import com.github.adamantcheese.model.di.annotation.VerboseLogs
import com.github.adamantcheese.model.repository.*
import dagger.BindsInstance
import dagger.Component
import okhttp3.Dns
import javax.inject.Singleton

@Singleton
@Component(
        modules = [
            NetworkModule::class,
            MainModule::class
        ]
)
interface MainComponent {
    fun inject(application: Application)

    fun getMediaServiceLinkExtraContentRepository(): MediaServiceLinkExtraContentRepository
    fun getSeenPostRepository(): SeenPostRepository
    fun getInlinedFileInfoRepository(): InlinedFileInfoRepository
    fun getChanPostRepository(): ChanPostRepository
    fun getThirdPartyArchiveInfoRepository(): ThirdPartyArchiveInfoRepository

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: Application): Builder
        @BindsInstance
        fun loggerTagPrefix(@LoggerTagPrefix loggerTagPrefix: String): Builder
        @BindsInstance
        fun verboseLogs(@VerboseLogs verboseLogs: Boolean): Builder
        @BindsInstance
        fun okHttpDns(@OkHttpDns dns: Dns): Builder
        @BindsInstance
        fun okHttpProtocols(@OkHttpProtocols okHttpProtocols: NetworkModule.OkHttpProtocolList): Builder
        @BindsInstance
        fun appConstants(appConstants: AppConstants): Builder

        fun build(): MainComponent
    }

}