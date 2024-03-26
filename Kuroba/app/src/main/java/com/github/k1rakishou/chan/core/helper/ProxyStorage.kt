package com.github.k1rakishou.chan.core.helper

import android.content.Context
import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.features.proxies.data.ProxyEntryView
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.ModularResult.Companion.value
import com.github.k1rakishou.common.jsonObject
import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.google.gson.annotations.Since
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("BlockingMethodInNonBlockingContext")
class ProxyStorage(
  private val appScope: CoroutineScope,
  private val appContext: Context,
  private val appConstants: AppConstants,
  private val verboseLogsEnabled: Boolean,
  private val siteResolver: SiteResolver,
  private val globalGson: Gson
) {
  private val proxiesFile = File(appContext.filesDir, appConstants.proxiesFileName)
  private val gson by lazy { initGson() }
  private val proxiesLoaded = AtomicBoolean(false)

  // ProxyStorage is dirty when the user has added/update or removed a proxy(ies) and haven't
  // restarted the app yet
  private val isProxyStorageDirty = AtomicBoolean(false)

  private val dependenciesInitialized = AtomicBoolean(false)

  @GuardedBy("this")
  private val proxiesMap = mutableMapOf<SiteDescriptor, MutableSet<ProxyKey>>()
  @GuardedBy("this")
  private val allProxiesMap = mutableMapOf<ProxyKey, KurobaProxy>()

  private val _proxyStorageUpdates = MutableSharedFlow<ProxyStorageUpdate>(extraBufferCapacity = 32)
  val proxyStorageUpdates: SharedFlow<ProxyStorageUpdate>
    get() = _proxyStorageUpdates.asSharedFlow()

  fun isDirty(): Boolean = isProxyStorageDirty.get()

  fun getCount(): Int = synchronized(this) { allProxiesMap.size }

  fun getNewProxyOrder(): Int = synchronized(this) {
    allProxiesMap.values
      .maxByOrNull { kurobaProxy -> kurobaProxy.order }?.order?.plus(1) ?: 0
  }

  fun getAllProxies(): List<KurobaProxy> {
    return synchronized(this) { allProxiesMap.values.toList() }
  }

  fun getProxyByUri(uri: URI, proxyActionType: ProxyActionType): List<Proxy> {
    loadProxies()
    awaitBlockingUntilDependenciesAreInitialized()

    val siteDescriptor = siteResolver.findSiteForUrl(uri.host.toString())?.siteDescriptor()
    if (siteDescriptor == null) {
      return emptyList()
    }

    return synchronized(this) {
      return@synchronized proxiesMap[siteDescriptor]
        ?.toList()
        ?.mapNotNull { proxyKey -> allProxiesMap[proxyKey] }
        ?.filter { kurobaProxy -> proxyActionType in kurobaProxy.supportedActions && kurobaProxy.enabled }
        ?.map { it.asJavaProxy }
        ?: emptyList()
    }
  }

  suspend fun enableDisableProxy(proxyEntryView: ProxyEntryView): ModularResult<Boolean> {
    val proxyKey = ProxyKey(proxyEntryView.address, proxyEntryView.port)

    val proxy = synchronized(this) { allProxiesMap[proxyKey] }
      ?: return value(false)

    val prevEnabledState = proxy.enabled
    proxy.enabled = !prevEnabledState

    val result = saveProxiesInternal()
    if (result is ModularResult.Error) {
      proxy.enabled = prevEnabledState
    } else {
      proxiesUpdated(ProxyStorageUpdate.ProxyUpdated(proxyKey))
    }

    return result
  }

  suspend fun deleteProxies(proxyKeys: List<ProxyKey>): ModularResult<Boolean> {
    if (proxyKeys.isEmpty()) {
      return value(false)
    }

    val oldProxyCopyList = mutableListOf<KurobaProxy>()

    synchronized(this) {
      proxyKeys.forEach { proxyKey ->
        val kurobaProxy = allProxiesMap.remove(proxyKey)

        if (kurobaProxy == null) {
          return@forEach
        }

        oldProxyCopyList += kurobaProxy.deepCopy()

        kurobaProxy.supportedSites.forEach { siteDescriptor ->
          proxiesMap.remove(siteDescriptor)
        }
      }
    }

    val saveResult = saveProxiesInternal()
    if (saveResult is ModularResult.Error) {
      synchronized(this) {
        oldProxyCopyList.forEach { kurobaProxy ->
          allProxiesMap[kurobaProxy.proxyKey] = kurobaProxy

          kurobaProxy.supportedSites.forEach { siteDescriptor ->
            proxiesMap[siteDescriptor]?.add(kurobaProxy.proxyKey)
          }
        }
      }
    } else {
      proxiesUpdated(ProxyStorageUpdate.ProxiesDeleted(proxyKeys))
    }

    return saveResult
  }

  suspend fun addNewProxy(newProxy: KurobaProxy): ModularResult<Boolean> {
    val oldProxyCopy = synchronized(this) {
      val proxyKey = newProxy.proxyKey

      val oldProxy = allProxiesMap[proxyKey]?.deepCopy()
      allProxiesMap[newProxy.proxyKey] = newProxy

      newProxy.supportedSites.forEach { siteDescriptor ->
        proxiesMap.putIfNotContains(siteDescriptor, mutableSetOf())
        proxiesMap[siteDescriptor]!!.add(proxyKey)
      }

      return@synchronized oldProxy
    }

    val saveResult = saveProxiesInternal()
    if (saveResult is ModularResult.Error) {
      if (oldProxyCopy != null) {
        synchronized(this) {
          val proxyKey = newProxy.proxyKey
          allProxiesMap[proxyKey] = oldProxyCopy

          oldProxyCopy.supportedSites.forEach { siteDescriptor ->
            proxiesMap.putIfNotContains(siteDescriptor, mutableSetOf())
            proxiesMap[siteDescriptor]!!.add(proxyKey)
          }
        }
      }
    } else {
      proxiesUpdated(ProxyStorageUpdate.ProxyCreated(newProxy.proxyKey))
    }

    return saveResult
  }

  fun loadProxies() {
    if (!proxiesLoaded.compareAndSet(false, true)) {
      return
    }

    Logger.d(TAG, "loadProxies()")

    try {
      synchronized(this) {
        if (!proxiesFile.exists()) {
          Logger.d(TAG, "proxiesFile does not exist, nothing to load")
          return@synchronized
        }

        val json = proxiesFile.readText()
        val kurobaProxies = gson.fromJson(json, KurobaProxies::class.java)

        kurobaProxies.proxies
          .map { kurobaProxyGson -> kurobaProxyGson.toKurobaProxy() }
          .forEach { kurobaProxy ->
            val proxyKey = kurobaProxy.proxyKey
            allProxiesMap[proxyKey] = kurobaProxy

            kurobaProxy.supportedSites.forEach { siteDescriptor ->
              proxiesMap.putIfNotContains(siteDescriptor, mutableSetOf())
              proxiesMap[siteDescriptor]!!.add(proxyKey)
            }
          }

        if (verboseLogsEnabled) {
          if (allProxiesMap.isEmpty()) {
            Logger.d(TAG, "loadProxies() No proxies to load")
          } else {
            allProxiesMap.forEach { (_, kurobaProxy) ->
              Logger.d(TAG, "loadProxies() Loaded proxy: $kurobaProxy")
            }
          }
        }

        proxiesUpdated(ProxyStorageUpdate.ProxiesInitialized)
      }
    } catch (error: Throwable) {
      Logger.e(TAG, "loadProxies() error", error)
    }
  }

  private fun awaitBlockingUntilDependenciesAreInitialized() {
    if (dependenciesInitialized.get()) {
      return
    }

    siteResolver.waitUntilInitialized()
    dependenciesInitialized.set(true)
  }

  private suspend fun saveProxiesInternal(): ModularResult<Boolean> {
    return withContext(Dispatchers.Default) {
      return@withContext Try {
        return@Try synchronized(this@ProxyStorage) {
          if (allProxiesMap.isEmpty()) {
            if (proxiesFile.exists()) {
              proxiesFile.delete()
            }

            Logger.d(TAG, "No proxies to save, deleting proxies file if it exists")
            return@synchronized true
          }

          if (!proxiesFile.exists()) {
            if (!proxiesFile.createNewFile()) {
              Logger.e(TAG, "saveProxiesInternal() failed to create proxies file: ${proxiesFile.absolutePath}")
              return@synchronized false
            }
          }

          val kurobaProxies = KurobaProxies(
            allProxiesMap.values.map { kurobaProxy -> kurobaProxy.toKurobaProxyGson() }
          )

          val json = gson.toJson(kurobaProxies)
          proxiesFile.writeText(json)

          if (verboseLogsEnabled) {
            allProxiesMap.forEach { (_, kurobaProxy) ->
              Logger.d(TAG, "Saved proxy: $kurobaProxy")
            }
          }

          return@synchronized true
        }
      }
    }
  }

  @Synchronized
  fun getProxyByProxyKey(proxyKey: ProxyKey): KurobaProxy? {
    return allProxiesMap[proxyKey]
  }

  private fun proxiesUpdated(proxyStorageUpdate: ProxyStorageUpdate) {
    if (proxyStorageUpdate !is ProxyStorageUpdate.ProxiesInitialized) {
      isProxyStorageDirty.set(true)
    }

    _proxyStorageUpdates.tryEmit(proxyStorageUpdate)
  }

  private fun initGson(): Gson {
    return globalGson.newBuilder()
      .setVersion(CURRENT_VERSION)
      .excludeFieldsWithoutExposeAnnotation()
      .registerTypeAdapter(ProxyActionType::class.java, object : TypeAdapter<ProxyActionType>() {
        override fun write(writer: JsonWriter, value: ProxyActionType) {
          writer.beginObject()
          writer.name(PROXY_SELECTOR_TYPE_NAME)
          writer.value(value.typeValue)
          writer.endObject()
        }

        override fun read(reader: JsonReader): ProxyActionType {
          var typeValue = -1

          reader.jsonObject {
            while (reader.hasNext()) {
              when (reader.nextName()) {
                PROXY_SELECTOR_TYPE_NAME -> typeValue = reader.nextInt()
                else -> reader.skipValue()
              }
            }
          }

          return ProxyActionType.fromTypeValue(typeValue)
        }
      })
      .registerTypeAdapter(KurobaProxyType::class.java, object : TypeAdapter<KurobaProxyType>() {
        override fun write(writer: JsonWriter, value: KurobaProxyType) {
          writer.beginObject()
          writer.name(KUROBA_PROXY_TYPE_NAME)
          writer.value(value.typeValue)
          writer.endObject()
        }

        override fun read(reader: JsonReader): KurobaProxyType {
          var typeValue = -1

          reader.jsonObject {
            while (reader.hasNext()) {
              when (reader.nextName()) {
                KUROBA_PROXY_TYPE_NAME -> typeValue = reader.nextInt()
                else -> reader.skipValue()
              }
            }
          }

          return KurobaProxyType.fromTypeValue(typeValue)
        }
      })
      .create()
  }

  private class KurobaProxies(
    @Expose(serialize = true, deserialize = true)
    @SerializedName("kuroba_proxies")
    val proxies: Collection<KurobaProxyGson>
  )

  data class ProxyKey(
    val address: String,
    val post: Int
  )

  class KurobaProxy(
    val address: String,
    val port: Int,
    @get:Synchronized
    @set:Synchronized
    var enabled: Boolean,
    val order: Int,
    val supportedSites: Set<SiteDescriptor>,
    val supportedActions: Set<ProxyActionType>,
    val proxyType: KurobaProxyType
  ) {

    val asJavaProxy by lazy {
      return@lazy Proxy(
        proxyType.toJavaProxyType(),
        InetSocketAddress(address, port)
      )
    }

    val proxyKey by lazy { ProxyKey(address, port) }

    fun toKurobaProxyGson(): KurobaProxyGson {
      return KurobaProxyGson(
        address = address,
        port = port,
        enabled = enabled,
        order = order,
        supportedSites = supportedSites,
        supportedActions = supportedActions,
        proxyType = proxyType
      )
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as KurobaProxy

      if (address != other.address) return false
      if (port != other.port) return false

      return true
    }

    override fun hashCode(): Int {
      var result = address.hashCode()
      result = 31 * result + port
      return result
    }

    override fun toString(): String {
      return "KurobaProxy(address='$address', port=$port, enabled=$enabled, order=$order, " +
        "supportedSites=$supportedSites, supportedSelectors=$supportedActions, proxyType=$proxyType)"
    }

    fun deepCopy(): KurobaProxy {
      return KurobaProxy(
        address,
        port,
        enabled,
        order,
        supportedSites.toSet(),
        supportedActions.toSet(),
        proxyType
      )
    }

  }

  data class KurobaProxyGson(
    @Since(1.0)
    @Expose(serialize = true, deserialize = true)
    @SerializedName("proxy_address")
    val address: String,
    @Since(1.0)
    @Expose(serialize = true, deserialize = true)
    @SerializedName("proxy_port")
    val port: Int,
    @Since(1.0)
    @Expose(serialize = true, deserialize = true)
    @SerializedName("proxy_enabled")
    val enabled: Boolean,
    @Since(1.0)
    @Expose(serialize = true, deserialize = true)
    @SerializedName("proxy_order")
    val order: Int,
    @Since(1.0)
    @Expose(serialize = true, deserialize = true)
    @SerializedName("proxy_supported_sites")
    val supportedSites: Set<SiteDescriptor>,
    @Since(1.0)
    @Expose(serialize = true, deserialize = true)
    @SerializedName("proxy_supported_selector_types")
    val supportedActions: Set<ProxyActionType>,
    @Since(1.0)
    @Expose(serialize = true, deserialize = true)
    @SerializedName("proxy_type")
    val proxyType: KurobaProxyType
  ) {

    fun toKurobaProxy(): KurobaProxy {
      return KurobaProxy(
        address = address,
        port = port,
        enabled = enabled,
        order = order,
        supportedSites = supportedSites,
        supportedActions = supportedActions,
        proxyType = proxyType
      )
    }

  }

  enum class KurobaProxyType(val typeValue: Int) {
    HTTP(0),
    SOCKS(1);

    fun toJavaProxyType(): Proxy.Type {
      return when (this) {
        HTTP -> Proxy.Type.HTTP
        SOCKS -> Proxy.Type.SOCKS
      }
    }

    companion object {
      fun fromTypeValue(typeValue: Int): KurobaProxyType {
        return when (typeValue) {
          0 -> HTTP
          1 -> SOCKS
          else -> throw RuntimeException("Unknown typeValue: ${typeValue}")
        }
      }
    }
  }

  enum class ProxyActionType(val typeValue: Int) {
    // Catalog/board loading requests, posting etc.
    SiteRequests(0),
    // Full image/webm etc loading
    SiteMediaFull(1),
    // Media previews loading (post image thumbnails/previews for bookmarks/tabs/notifications etc)
    SiteMediaPreviews(2);

    companion object {
      fun fromTypeValue(typeValue: Int): ProxyActionType {
        return when (typeValue) {
          0 -> SiteRequests
          1 -> SiteMediaFull
          2 -> SiteMediaPreviews
          else -> throw RuntimeException("Unknown typeValue: ${typeValue}")
        }
      }
    }
  }

  sealed class ProxyStorageUpdate {
    object ProxiesInitialized : ProxyStorageUpdate()
    class ProxyCreated(val proxyKey: ProxyKey) : ProxyStorageUpdate()
    class ProxyUpdated(val proxyKey: ProxyKey) : ProxyStorageUpdate()
    class ProxiesDeleted(val proxyKeyList: List<ProxyKey>) : ProxyStorageUpdate()
  }

  companion object {
    private const val TAG = "ProxyStorage"
    private const val CURRENT_VERSION = 1.0
    private const val PROXY_SELECTOR_TYPE_NAME = "proxy_selector_type"
    private const val KUROBA_PROXY_TYPE_NAME = "kuroba_proxy_type"
  }

}