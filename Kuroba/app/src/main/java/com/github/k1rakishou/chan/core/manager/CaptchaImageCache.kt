package com.github.k1rakishou.chan.core.manager

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import java.io.ByteArrayOutputStream
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


class CaptchaImageCache {
    private val rwLock = ReentrantReadWriteLock()

    @GuardedBy("rwLock")
    private val cache = mutableMapWithCap<Key, Value>(MAX_SIZE)

    fun put(uuid: String, chanDescriptor: ChanDescriptor, bitmap: Bitmap) {
        rwLock.write {
            evictOld()

            val key = Key(uuid, chanDescriptor)
            val value = Value(bitmap.toByteArray(), SystemClock.elapsedRealtime())

            cache.put(key, value)
        }
    }

    fun consume(uuid: String, chanDescriptor: ChanDescriptor): ByteArray? {
        return rwLock.read {
            val key = Key(uuid, chanDescriptor)
            return@read cache.remove(key)?.bitmapBytes
        }
    }

    private fun evictOld() {
        check(rwLock.isWriteLocked) { "rwLock must be write locked" }

        val toDelete = mutableSetOf<Key>()

        cache.entries
            .sortedByDescending { it.value.time }
            .drop(MAX_SIZE)
            .forEach { (key, _) -> toDelete += key }

        if (toDelete.isNotEmpty()) {
            toDelete.forEach { key -> cache.remove(key) }
        }
    }

    private fun Bitmap.toByteArray(): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray = stream.toByteArray()
        recycle()

        return byteArray
    }

    private data class Key(
        val uuid: String,
        val chanDescriptor: ChanDescriptor
    )

    private data class Value(
        val bitmapBytes: ByteArray,
        val time: Long
    )

    companion object {
        private const val MAX_SIZE = 16
    }

}