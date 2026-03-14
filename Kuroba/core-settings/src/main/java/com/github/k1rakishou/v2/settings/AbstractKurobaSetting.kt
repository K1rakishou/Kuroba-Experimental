package com.github.k1rakishou.v2.settings

import androidx.room.concurrent.AtomicBoolean
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettingInfo
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.database.KurobaSettingDao
import com.github.k1rakishou.v2.database.KurobaSettingEntity
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

abstract class AbstractKurobaSetting<ValueType, StorageType>(
  private val database: KurobaSettingsDatabase,
  private val kurobaSettingInfo: KurobaSettingInfo
) {
  private val _scopeJob = SupervisorJob()
  private val _coroutineScope = CoroutineScope(_scopeJob + Dispatchers.Main)
  private val _tag = this::class.java.simpleName
  private val _mutex = Mutex()
  private val _initializedFromInitialState = AtomicBoolean(false)

  protected val settingState by lazy { MutableStateFlow<StateValue<ValueType>>(StateValue.Uninitialized) }
  protected val settingDao: KurobaSettingDao by lazy { database.settingDao }

  abstract val key: KurobaSettingKey
  abstract val default: ValueType

  protected abstract suspend fun deserialize(buffer: ByteBuffer): StorageType
  protected abstract suspend fun serialize(value: StorageType): ByteBuffer

  protected open suspend fun mapStorageToValueType(st: StorageType): ValueType {
    return st as ValueType
  }

  protected open suspend fun mapValueToStorageType(vt: ValueType): StorageType {
    return vt as StorageType
  }

  fun readBlocking(): ValueType {
    tryInitializeFromInitialState()

    val fromCache = settingState.value
    if (fromCache is StateValue.Initialized) {
      // Fast path, value was already cached
      return fromCache.value
    }

    // Slow path, read from the database
    return runBlocking { read() }
  }

  open suspend fun read(): ValueType {
    tryInitializeFromInitialState()

    val fromCache = settingState.value
    if (fromCache is StateValue.Initialized) {
      // Fast path, setting is already initialized
      return fromCache.value
    }

    return _mutex.withLock {
      val fromCache = settingState.value
      if (fromCache is StateValue.Initialized) {
        // Still kinda fast path, but we are using mutex now to avoid multi-reading from the database
        return@withLock fromCache.value
      }

      val (value, duration) = measureTimedValue {
        withContext(Dispatchers.IO) {
          try {
            val value = readFromDatabase()
              ?.let { entity ->
                if (entity.value == null) {
                  return@let default
                }

                val byteBuffer = ByteBuffer.wrap(entity.value)
                return@let mapStorageToValueType(deserialize(byteBuffer))
              } ?: default

            settingState.value = StateValue.Initialized(value)
            return@withContext value
          } catch (error: Throwable) {
            Logger.error(this::class.java.simpleName, error) { "Failed to read setting '${key}', resetting." }

            removeSettingAndReset()
            settingState.value = StateValue.Initialized(default)

            return@withContext default
          }
        }
      }

      Logger.debug(_tag) { "read(${key.raw}) took ${duration}" }
      return@withLock value
    }
  }

  fun writeAsync(value: ValueType) {
    _coroutineScope.launch { write(value) }
  }

  fun writeBlocking(value: ValueType) {
    runBlocking { write(value) }
  }

  open suspend fun write(value: ValueType) {
    val fromCache = settingState.value
    if (fromCache is StateValue.Initialized && fromCache.value == value) {
      return
    }

    _mutex.withLock {
      val fromCache = settingState.value
      if (fromCache is StateValue.Initialized && fromCache.value == value) {
        return@withLock
      }

      val duration = measureTime {
        try {
          settingState.value = StateValue.Initialized(value)

          withContext(Dispatchers.IO) {
            val byteBuffer = serialize(mapValueToStorageType(value))

            val entity = KurobaSettingEntity(
              key = key.raw,
              value = byteBuffer.array(),
              backupable = kurobaSettingInfo.backupable
            )

            writeToDatabase(entity)
          }
        } catch (error: Throwable) {
          Logger.error(_tag, error) { "Failed to write setting '${key}', resetting." }
          settingState.value = StateValue.Initialized(value)
          removeSettingAndReset()
        }
      }

      Logger.debug(_tag) { "write(${key.raw}) took ${duration}" }
    }
  }

  fun resetBlocking() {
    runBlocking { reset() }
  }

  suspend fun reset() {
    write(default)
  }

  suspend fun isNotDefault(): Boolean = read() != default

  fun listen(): Flow<ValueType> {
    tryInitializeFromInitialState()

    return settingState
      .mapNotNull { stateValue -> (stateValue as? StateValue.Initialized)?.value }
  }

  protected suspend fun readFromDatabase(): KurobaSettingEntity? {
    return settingDao.selectByKey(key.raw)
  }

  protected suspend fun writeToDatabase(entity: KurobaSettingEntity) {
    settingDao.upsert(entity)
  }

  private suspend fun removeSettingAndReset() {
    settingDao.deleteByKey(key.raw)

    val entity = KurobaSettingEntity(
      key = key.raw,
      value = serialize(mapValueToStorageType(default)).array(),
      backupable = kurobaSettingInfo.backupable
    )
    writeToDatabase(entity)
  }

  private fun tryInitializeFromInitialState() {
    if (settingState.value is StateValue.Initialized) {
      return
    }

    synchronized(this) {
      if (settingState.value is StateValue.Initialized) {
        return@synchronized
      }

      if (!_initializedFromInitialState.compareAndSet(false, true)) {
        return@synchronized
      }

      val settingValueBytes = kurobaSettingInfo.initialSettingsState.allSettings[key.raw]
      if (settingValueBytes == null) {
        settingState.value = StateValue.Initialized(default)
        return@synchronized
      }

      val byteBuffer = ByteBuffer.wrap(settingValueBytes)
      settingState.value = runBlocking { StateValue.Initialized(mapStorageToValueType(deserialize(byteBuffer))) }
    }
  }

  protected sealed interface StateValue<out T> {
    data object Uninitialized : StateValue<Nothing>
    data class Initialized<T>(val value: T) : StateValue<T>
  }
}