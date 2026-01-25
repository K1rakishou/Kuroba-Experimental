package com.github.k1rakishou.chan.core.di.module.application

import com.github.k1rakishou.chan.ui.captcha.dvach.DvachCaptchaLayoutViewModel
import com.github.k1rakishou.common.KurobaCookieExpirationAdapter
import com.github.k1rakishou.common.jsonObject
import com.github.k1rakishou.common.nextStringOrNull
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.DescriptorParcelableMoshiAdapter
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class JsonParserModule {

  @Provides
  @Singleton
  fun provideGson(): Gson {
    Logger.deps("Gson");

    return GsonBuilder()
      .registerSiteDescriptorType()
      .create()
  }

  private fun GsonBuilder.registerSiteDescriptorType(): GsonBuilder {
    registerTypeAdapter(SiteDescriptor::class.java, object : TypeAdapter<SiteDescriptor>() {
      override fun write(writer: JsonWriter, value: SiteDescriptor) {
        writer.beginObject()
        writer.name("site_name")
        writer.value(value.siteName)
        writer.endObject()
      }

      override fun read(reader: JsonReader): SiteDescriptor {
        var siteName: String? = null

        reader.jsonObject {
          while (reader.hasNext()) {
            when (reader.nextName()) {
              "site_name" -> siteName = reader.nextStringOrNull()
              else -> reader.skipValue()
            }
          }
        }

        return SiteDescriptor.create(
          requireNotNull(siteName) { "siteName is null" }
        )
      }
    })

    return this
  }

  @Provides
  @Singleton
  fun provideMoshi(): Moshi {
    Logger.deps("Moshi");

    return Moshi.Builder()
      .add(DescriptorParcelableMoshiAdapter())
      .add(DvachCaptchaLayoutViewModel.EmojiCaptchaInfo.Adapter())
      .add(KurobaCookieExpirationAdapter())
      .build()
  }

}