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
package com.github.k1rakishou.core_logger

import android.util.Log
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat

object Logger {
    private var tagPrefix: String? = null
    private var isCurrentBuildDev = false
    const val DI_TAG = "Dependency Injection"

    private val logTimeFormatter = DateTimeFormatterBuilder()
        .append(ISODateTimeFormat.hourMinuteSecondMillis())
        .toFormatter()

    fun init(prefix: String?, isDevBuild: Boolean) {
        tagPrefix = prefix
        isCurrentBuildDev = isDevBuild
    }

    private val time: String
        get() {
            if (isCurrentBuildDev) {
                return ""
            }

            return "(" + logTimeFormatter.print(DateTime.now()) + ") "
        }

    @JvmStatic
    fun d(tag: String, message: String) {
        if (canLog()) {
            Log.d(time + tagPrefix + tag, message)
        }
    }

    @JvmStatic
    fun w(tag: String, message: String) {
        Log.w(time + tagPrefix + tag, message)
    }

    @JvmStatic
    fun e(tag: String, message: String) {
        Log.e(time + tagPrefix + tag, message)
    }

    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(time + tagPrefix + tag, message, throwable)
    }

    @JvmStatic
    fun deps(message: String) {
        if (!isCurrentBuildDev) {
            return
        }

        val tag = DI_TAG + " (" + Thread.currentThread().name + ":" + Thread.currentThread().id + ")"
        d(tag, message)
    }

    // ========================================================

    fun debug(tag: String, message: () -> String) {
        if (canLog()) {
            Log.d(time + tagPrefix + tag, message())
        }
    }

    fun Any.debug(tag: String? = null, message: () -> String) {
        if (canLog()) {
            Log.d(time + tagPrefix + (tag ?: outerClassName()), message())
        }
    }

    fun warning(tag: String, message: () -> String) {
        if (canLog()) {
            Log.w(time + tagPrefix + tag, message())
        }
    }

    fun Any.warning(tag: String? = null, message: () -> String) {
        if (canLog()) {
            Log.w(time + tagPrefix + (tag ?: outerClassName()), message())
        }
    }

    fun error(tag: String, message: () -> String) {
        if (canLog()) {
            Log.e(time + tagPrefix + tag, message())
        }
    }

    fun Any.error(tag: String? = null, message: () -> String) {
        if (canLog()) {
            Log.e(time + tagPrefix + (tag ?: outerClassName()), message())
        }
    }

    fun error(tag: String, throwable: Throwable, message: () -> String) {
        if (canLog()) {
            Log.e(time + tagPrefix + tag, message(), throwable)
        }
    }

    fun Any.error(tag: String? = null, throwable: Throwable, message: () -> String) {
        if (canLog()) {
            Log.e(time + tagPrefix + (tag ?: outerClassName()), message(), throwable)
        }
    }

    @PublishedApi
    internal fun Any.outerClassName(): String {
        val javaClass = this::class.java
        val fullClassName = javaClass.name
        val outerClassName = fullClassName.substringBefore('$')
        val simplerOuterClassName = outerClassName.substringAfterLast('.')
        return if (simplerOuterClassName.isEmpty()) {
            fullClassName
        } else {
            simplerOuterClassName.removeSuffix("Kt")
        }
    }


    private fun canLog(): Boolean {
        return true
    }

}
