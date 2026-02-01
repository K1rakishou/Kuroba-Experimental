package com.github.k1rakishou.chan.features.webview

import android.content.Context
import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class WebViewLastTouchPositionHolder(
  private val appContext: Context,
  private val moshi: Moshi
) {
  private val _lock = ReentrantReadWriteLock()
  @GuardedBy("_lock")
  @Volatile
  private var _cached: LastTouchPositionInfoHolder? = null

  fun get(taskId: String, siteName: String): TouchPosition? {
    val screenOrientation = AppModuleAndroidUtils.screenOrientation

    return _lock.read {
      val lastTouchPositions = getOrInit()

      val index = findIndex(lastTouchPositions, taskId, siteName, screenOrientation)
      if (index < 0) {
        return@read null
      }

      return@read lastTouchPositions.positions[index].touchPosition
    }
  }

  fun update(taskId: String, siteName: String, touchPosition: TouchPosition) {
    val screenOrientation = AppModuleAndroidUtils.screenOrientation

    _lock.write {
      val lastTouchPositions = getOrInit()

      val index = findIndex(lastTouchPositions, taskId, siteName, screenOrientation)
      if (index < 0) {
        val new = TouchPositionInfo(
          taskId = taskId,
          siteName = siteName,
          screenOrientation = screenOrientation,
          touchPosition = touchPosition
        )

        lastTouchPositions.positions.add(new)
      } else {
        val updated = lastTouchPositions.positions[index].copy(touchPosition = touchPosition)
        lastTouchPositions.positions[index] = updated
      }
    }
  }

  fun persist() {
    if (_cached != null) {
      _lock.read {
        if (_cached != null) {
          val file = File(appContext.noBackupFilesDir, "webview_last_touch_positions.json")
          if (!file.exists()) {
            file.createNewFile()
          }

          val json = moshi
            .adapter(LastTouchPositionInfoHolderJson::class.java)
            .toJson(_cached!!.toLastTouchPositionsPerWebViewTaskJson())

          file.writeText(json)
        }
      }
    }
  }

  private fun findIndex(
    lastTouchPositions: LastTouchPositionInfoHolder,
    taskId: String,
    siteName: String,
    screenOrientation: Int
  ): Int {
    return _lock.read {
      return@read lastTouchPositions.positions.indexOfFirst { positionInfo ->
        positionInfo.taskId == taskId &&
          positionInfo.siteName == siteName &&
          positionInfo.screenOrientation == screenOrientation
      }
    }
  }

  private fun getOrInit(): LastTouchPositionInfoHolder {
    if (_cached == null) {
      _lock.write {
        if (_cached == null) {
          val file = File(appContext.noBackupFilesDir, "webview_last_touch_positions.json")

          try {
            if (!file.exists()) {
              file.createNewFile()
              _cached = LastTouchPositionInfoHolder(mutableListOf())
              return@write
            }

            _cached = moshi
              .adapter(LastTouchPositionInfoHolderJson::class.java)
              .fromJson(file.readText())
              ?.toLastTouchPositionsPerWebViewTask()
          } catch (error: Throwable) {
            Logger.error(TAG, error) { "Failed to initialize from file" }

            file.delete()
            _cached = LastTouchPositionInfoHolder(mutableListOf())
          }
        }
      }
    }

    return _cached!!
  }

  data class LastTouchPositionInfoHolder(
    val positions: MutableList<TouchPositionInfo>
  ) {
    fun toLastTouchPositionsPerWebViewTaskJson(): LastTouchPositionInfoHolderJson {
      return LastTouchPositionInfoHolderJson(
        positions = positions.toList()
      )
    }
  }

  @JsonClass(generateAdapter = true)
  data class LastTouchPositionInfoHolderJson(
    val positions: List<TouchPositionInfo>
  ) {
    fun toLastTouchPositionsPerWebViewTask(): LastTouchPositionInfoHolder {
      return LastTouchPositionInfoHolder(
        positions = positions.toMutableList()
      )
    }
  }

  @JsonClass(generateAdapter = true)
  data class TouchPositionInfo(
    val taskId: String,
    val siteName: String,
    val screenOrientation: Int,
    val touchPosition: TouchPosition
  )

  @JsonClass(generateAdapter = true)
  data class TouchPosition(
    val x: Float,
    val y: Float
  )

  companion object {
    private const val TAG = "WebViewLastTouchPositionHolder"
  }
}