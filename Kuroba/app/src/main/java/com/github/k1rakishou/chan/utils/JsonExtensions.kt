package com.github.k1rakishou.chan.utils

import com.squareup.moshi.JsonReader

fun JsonReader.traverseJson(
  visitor: (List<String>, String?, String?) -> Unit,
  currentPath: List<String> = emptyList(),
  currentName: String?,
  jsonDebugOutput: StringBuilder? = null
) {
  var prevToken: JsonReader.Token? = null

  while (this.hasNext()) {
    val token = this.peek()

    when (token) {
      JsonReader.Token.BEGIN_ARRAY -> {
        jsonDebugOutput?.append("[")

        this.beginArray()

        while (peek() != JsonReader.Token.END_ARRAY) {
          traverseJson(visitor, currentPath, currentName, jsonDebugOutput)
        }

        this.endArray()

        jsonDebugOutput?.append("]")
        return
      }
      JsonReader.Token.BEGIN_OBJECT -> {
        val needAddComma = prevToken == JsonReader.Token.BEGIN_OBJECT

        if (needAddComma) {
          jsonDebugOutput?.append(",{")
        } else {
          jsonDebugOutput?.append("{")
        }

        this.beginObject()
        traverseJson(visitor, currentPath, currentName, jsonDebugOutput)
        this.endObject()

        jsonDebugOutput?.append("}")
        return
      }
      JsonReader.Token.END_ARRAY -> error("END_ARRAY must be unreachable")
      JsonReader.Token.END_OBJECT -> error("END_OBJECT must be unreachable")
      null -> {
        this.skipValue()
        return
      }
      JsonReader.Token.END_DOCUMENT -> {
        jsonDebugOutput?.appendLine()
        return
      }
      JsonReader.Token.NAME -> {
        val name = this.nextName()
        val valueToken = this.peek()

        val needAddComma = if (prevToken != null) {
          when (token) {
            JsonReader.Token.NAME,
            JsonReader.Token.STRING,
            JsonReader.Token.NUMBER,
            JsonReader.Token.BOOLEAN,
            JsonReader.Token.END_OBJECT,
            JsonReader.Token.NULL -> true
            JsonReader.Token.BEGIN_ARRAY,
            JsonReader.Token.END_ARRAY,
            JsonReader.Token.BEGIN_OBJECT,
            JsonReader.Token.END_DOCUMENT,
            null -> false
          }
        } else {
          false
        }

        if (needAddComma) {
          jsonDebugOutput?.append(",\"$name\":")
        } else {
          jsonDebugOutput?.append("\"$name\":")
        }

        when (valueToken) {
          JsonReader.Token.STRING,
          JsonReader.Token.NUMBER,
          JsonReader.Token.BOOLEAN,
          JsonReader.Token.NULL -> {
            parseJsonValue(valueToken, currentPath, name, visitor, jsonDebugOutput)
          }
          JsonReader.Token.BEGIN_ARRAY,
          JsonReader.Token.END_ARRAY,
          JsonReader.Token.BEGIN_OBJECT,
          JsonReader.Token.END_OBJECT -> {
            traverseJson(visitor, currentPath + name, name, jsonDebugOutput)
          }
          JsonReader.Token.NAME,
          JsonReader.Token.END_DOCUMENT -> error("Unreachable! valueToken=$valueToken")
          null -> error("Token is null!")
        }
      }
      JsonReader.Token.STRING,
      JsonReader.Token.NUMBER,
      JsonReader.Token.BOOLEAN,
      JsonReader.Token.NULL -> {
        parseJsonValue(token, currentPath, null, visitor, jsonDebugOutput)
      }
    }

    prevToken = token
  }
}

private fun JsonReader.parseJsonValue(
  valueToken: JsonReader.Token,
  currentPath: List<String>,
  name: String?,
  visitor: (List<String>, String?, String?) -> Unit,
  jsonOutput: StringBuilder?,
) {
  when (valueToken) {
    JsonReader.Token.STRING -> {
      val value = this.nextString()
      jsonOutput?.append("\"$value\"")

      visitor(currentPath, name, value)
    }
    JsonReader.Token.NUMBER -> {
      val value = parseNumber()
      jsonOutput?.append("$value")

      visitor(currentPath, name, value)
    }
    JsonReader.Token.BOOLEAN -> {
      val value = this.nextBoolean().toString()
      jsonOutput?.append(value)

      visitor(currentPath, name, value)
    }
    JsonReader.Token.NULL -> {
      val value = this.nextNull<String>()
      jsonOutput?.append("$value")

      visitor(currentPath, name, value)
    }
    else -> error("Unreachable! valueToken=$valueToken")
  }
}

private fun JsonReader.parseNumber(): String? {
  val asInt = try {
    this.nextInt()
  } catch (error: Throwable) {
    null
  }

  if (asInt != null) {
    return asInt.toString()
  }

  val asLong = try {
    this.nextLong()
  } catch (error: Throwable) {
    null
  }

  if (asLong != null) {
    return asLong.toString()
  }

  val asDouble = try {
    this.nextDouble()
  } catch (error: Throwable) {
    null
  }

  if (asDouble != null) {
    return asDouble.toString()
  }

  this.skipValue()
  return null
}