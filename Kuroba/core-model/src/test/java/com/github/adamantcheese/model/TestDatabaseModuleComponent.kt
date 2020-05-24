package com.github.adamantcheese.model

import android.app.Application
import androidx.room.Room
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.source.local.ChanPostLocalSource
import com.github.adamantcheese.model.source.local.InlinedFileInfoLocalSource
import com.github.adamantcheese.model.source.local.MediaServiceLinkExtraContentLocalSource
import com.github.adamantcheese.model.source.remote.InlinedFileInfoRemoteSource
import com.github.adamantcheese.model.source.remote.MediaServiceLinkExtraContentRemoteSource
import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.TimeUnit

class TestDatabaseModuleComponent(
        private val application: Application = RuntimeEnvironment.application
) {
    private val logger = Logger(true)
    private var inMemoryDatabase: KurobaDatabase? = null
    private var onDiskDatabase: KurobaDatabase? = null
    private var okHttpClient: OkHttpClient? = null
    private var gson: Gson? = null

    fun provideLogger() = logger

    fun provideGson(): Gson {
        if (gson == null) {
            gson = Gson().newBuilder().create()
        }

        return gson!!
    }

    fun provideOkHttpClient(): OkHttpClient {
        if (okHttpClient == null) {
            okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .writeTimeout(5, TimeUnit.SECONDS)
                    .build()
        }

        return okHttpClient!!
    }

    fun provideInMemoryKurobaDatabase(): KurobaDatabase {
        if (inMemoryDatabase != null) {
            return inMemoryDatabase!!
        }

        inMemoryDatabase = Room.inMemoryDatabaseBuilder(
                        application.applicationContext,
                        KurobaDatabase::class.java
                )
                .build()

        return inMemoryDatabase!!
    }

    fun provideOnDiskKurobaDatabase(): KurobaDatabase {
        if (onDiskDatabase != null) {
            return onDiskDatabase!!
        }

        onDiskDatabase = Room.databaseBuilder(
          application.applicationContext,
          KurobaDatabase::class.java,
          "kuroba-test.db"
        )
          .build()

        return onDiskDatabase!!
    }

    /**
     * Local source
     * */

    fun provideInlinedFileInfoLocalSource(): InlinedFileInfoLocalSource {
        return InlinedFileInfoLocalSource(
                provideInMemoryKurobaDatabase(),
                "InlinedFileInfoLocalSource",
                provideLogger()
        )
    }

    fun provideChanPostLocalSource(database: KurobaDatabase = provideInMemoryKurobaDatabase()): ChanPostLocalSource {
        return ChanPostLocalSource(
          database,
          "ChanPostLocalSource",
          provideLogger(),
          provideGson()
        )
    }

    fun provideMediaServiceLinkExtraContentLocalSource(): MediaServiceLinkExtraContentLocalSource {
        return MediaServiceLinkExtraContentLocalSource(
                provideInMemoryKurobaDatabase(),
                "MediaServiceLinkExtraContentLocalSource",
                provideLogger()
        )
    }

    /**
     * Remote source
     * */

    fun provideInlinedFileInfoRemoteSource(): InlinedFileInfoRemoteSource {
        return InlinedFileInfoRemoteSource(
                provideOkHttpClient(),
                "InlinedFileInfoRemoteSource",
                provideLogger()
        )
    }

    fun provideMediaServiceLinkExtraContentRemoteSource(): MediaServiceLinkExtraContentRemoteSource {
        return MediaServiceLinkExtraContentRemoteSource(
                provideOkHttpClient(),
                "MediaServiceLinkExtraContentRemoteSource",
                provideLogger()
        )
    }
}