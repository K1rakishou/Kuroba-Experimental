package com.github.k1rakishou.chan.utils

import com.github.k1rakishou.chan.core.loader.impl.ThirdEyeLoader
import com.github.k1rakishou.common.mutableListWithCap
import com.squareup.moshi.JsonReader
import junit.framework.Assert.assertEquals
import okio.buffer
import okio.source
import org.junit.Test

class JsonExtensionsKtTest {

  @Test
  fun test() {
    val json = """
      {
        "post": {
          "file": {
            "width": 1000,
            "height": 632,
            "ext": "jpg",
            "size": 112233,
            "url": "https://full.jpg"
          },
          "preview": {
            "width": 150,
            "height": 94,
            "url": "https://preview.jpg"
          },
          "tags": {
            "gr1": [
              "gr1_test1",
              "gr1_test2",
              "gr1_test3"
            ],
            "gr2": [
              "gr2_test1",
              "gr2_test2",
              "gr2_test3"
            ],
            "gr3": [
              "gr3_test1",
              "gr3_test2",
              "gr3_test3"
            ]
          },
          "tags2": [
            {
              "name": "test1"
            },
            {
              "name": "test2"
            },
            {
              "name": "test3"
            }
          ]
        }
      }
    """.trimIndent()

    val fullUrlJsonKey = ThirdEyeLoader.JsonKey("post > file > url")
    val previewUrlJsonKey = ThirdEyeLoader.JsonKey("post > preview > url")
    val fileSizeJsonKey = ThirdEyeLoader.JsonKey("post > file > size")
    val widthJsonKey = ThirdEyeLoader.JsonKey("post > file > width")
    val heightJsonKey = ThirdEyeLoader.JsonKey("post > file > height")
    val tagsJsonKey = ThirdEyeLoader.JsonKey("post > tags > *")
    val tags2JsonKey = ThirdEyeLoader.JsonKey("post > tags2 > *")

    val namesToCheck = mutableMapOf<ThirdEyeLoader.JsonKey, ThirdEyeLoader.JsonValue?>()
    namesToCheck[fullUrlJsonKey] = null
    namesToCheck[previewUrlJsonKey] = null
    namesToCheck[fileSizeJsonKey] = null
    namesToCheck[widthJsonKey] = null
    namesToCheck[heightJsonKey] = null
    namesToCheck[tagsJsonKey] = null
    namesToCheck[tags2JsonKey] = null

    json.byteInputStream().source().buffer().use {
      JsonReader.of(it).use { jsonReader ->
        jsonReader.traverseJson(
          visitor = { path, name, value ->
            for (jsonKey in namesToCheck.keys) {
              if (jsonKey.compare(path, name)) {
                if (namesToCheck[jsonKey] is ThirdEyeLoader.JsonValue.JsonString) {
                  val prevValue = (namesToCheck[jsonKey] as ThirdEyeLoader.JsonValue.JsonString).value

                  val list = mutableListWithCap<String>(10).apply {
                    if (prevValue != null) {
                      add(prevValue)
                    }

                    if (value != null) {
                      add(value)
                    }
                  }

                  namesToCheck[jsonKey] = ThirdEyeLoader.JsonValue.JsonArray(list)
                } else if (namesToCheck[jsonKey] is ThirdEyeLoader.JsonValue.JsonArray) {
                  if (value != null) {
                    (namesToCheck[jsonKey] as ThirdEyeLoader.JsonValue.JsonArray).values.add(value)
                  }
                } else {
                  namesToCheck[jsonKey] = ThirdEyeLoader.JsonValue.JsonString(value)
                }

                break
              }
            }
          },
          currentName = null,
          jsonDebugOutput = null
        )
      }
    }

    assertEquals("https://full.jpg", namesToCheck[fullUrlJsonKey]?.firstOrNull())
    assertEquals("https://preview.jpg", namesToCheck[previewUrlJsonKey]?.firstOrNull())
    assertEquals("112233", namesToCheck[fileSizeJsonKey]?.firstOrNull())
    assertEquals("1000", namesToCheck[widthJsonKey]?.firstOrNull())
    assertEquals("632", namesToCheck[heightJsonKey]?.firstOrNull())
    assertEquals("gr1_test1,gr1_test2,gr1_test3,gr2_test1,gr2_test2,gr2_test3,gr3_test1,gr3_test2,gr3_test3", namesToCheck[tagsJsonKey]?.asString(separator = ","))
    assertEquals("test1,test2,test3", namesToCheck[tags2JsonKey]?.asString(separator = ","))
  }

}