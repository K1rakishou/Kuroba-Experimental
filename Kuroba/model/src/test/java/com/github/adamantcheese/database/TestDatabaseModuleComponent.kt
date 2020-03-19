package com.github.adamantcheese.database

import android.app.Application
import androidx.room.Room
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.source.local.InlinedFileInfoLocalSource
import com.github.adamantcheese.database.source.remote.InlinedFileInfoRemoteSource
import okhttp3.OkHttpClient
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.TimeUnit

class TestDatabaseModuleComponent(
        private val application: Application = RuntimeEnvironment.application
) {
    private val logger = Logger(true)
    private var database: KurobaDatabase? = null
    private var okHttpClient: OkHttpClient? = null

    fun provideLogger() = logger

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

    fun provideKurobaDatabase(): KurobaDatabase {
        if (database != null) {
            return database!!
        }

        database = Room.inMemoryDatabaseBuilder(
                        application.applicationContext,
                        KurobaDatabase::class.java
                )
                .build()

        return database!!
    }

    fun provideInlinedFileInfoLocalSource(): InlinedFileInfoLocalSource {
        return InlinedFileInfoLocalSource(
                provideKurobaDatabase(),
                "InlinedFileInfoLocalSource",
                provideLogger()
        )
    }

    fun provideInlinedFileInfoRemoteSource(): InlinedFileInfoRemoteSource {
        return InlinedFileInfoRemoteSource(
                provideOkHttpClient(),
                "InlinedFileInfoRemoteSource",
                provideLogger()
        )
    }
}