/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.di.module.application

import com.github.k1rakishou.common.jsonObject
import com.github.k1rakishou.common.nextStringOrNull
import com.github.k1rakishou.json.BooleanJsonSetting
import com.github.k1rakishou.json.IntegerJsonSetting
import com.github.k1rakishou.json.JsonSetting
import com.github.k1rakishou.json.LongJsonSetting
import com.github.k1rakishou.json.RuntimeTypeAdapterFactory
import com.github.k1rakishou.json.StringJsonSetting
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
    val userSettingAdapter = RuntimeTypeAdapterFactory.of(JsonSetting::class.java, "type")
      .registerSubtype(StringJsonSetting::class.java, "string")
      .registerSubtype(IntegerJsonSetting::class.java, "integer")
      .registerSubtype(LongJsonSetting::class.java, "long")
      .registerSubtype(BooleanJsonSetting::class.java, "boolean")

    return GsonBuilder()
      .registerTypeAdapterFactory(userSettingAdapter)
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
    return Moshi.Builder()
      .build()
  }

}