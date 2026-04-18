package com.github.k1rakishou.chan.core.net.update

import android.os.Build
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.usecase.LoadChangelogUseCase
import com.github.k1rakishou.chan.utils.ReleaseHelpers
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.suspendConvertIntoJsonObjectWithAdapter
import com.github.k1rakishou.core_logger.Logger
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class UpdateApiRequest(
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val loadChangelogUseCase: LoadChangelogUseCase,
  private val moshi: Moshi,
  private val canUpdateToPrerelease: Boolean
) {
  private val listOfReleasesAdapter = moshi.adapter<List<Release>>(
    Types.newParameterizedType(List::class.java, Release::class.java)
  )
  
  suspend fun execute(): ModularResult<ApkReleaseInfo> {
    return ModularResult.Try {
      val request = Request.Builder()
        .url("https://api.github.com/repos/K1rakishou/Kuroba-Experimental/releases")
        .get()
        .build()

      val releases = proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
        request = request,
        adapter = listOfReleasesAdapter
      )
        .mapError { throwable -> UpdateRequestError(throwable.message ?: throwable.errorMessageOrClassName()) }
        .unwrap()
        ?: throw UpdateRequestError("Failed to get a list of releases from Github")

      var apkUpdateInfo = convertFirstSuitableRelease(releases)
        ?: throw UpdateRequestError("Failed to find a suitable release")

      val changelogResult = loadChangelogUseCase.execute(
        parameter = LoadChangelogUseCase.Params(
          versionCode = apkUpdateInfo.versionCode.code
        )
      )

      when (changelogResult) {
        is ModularResult.Error<*> -> {
          // no-op, use changelog from the release page (last commits)
        }
        is ModularResult.Value<String> -> {
          apkUpdateInfo = apkUpdateInfo.copy(releaseDescription = changelogResult.value)
        }
      }

      return@Try apkUpdateInfo
    }
  }

  private fun convertFirstSuitableRelease(releases: List<Release>): ApkReleaseInfo? {
    for (release in releases) {
      val prerelease = release.prerelease
        ?: continue

      if (!canUpdateToPrerelease && prerelease) {
        continue
      }

      val tagName = release.tagName
      if (tagName.isNullOrBlank()) {
        continue
      }

      val versionCode = readVersionCode(release)
      if (versionCode == null) {
        continue
      }

      val releaseTitle = release.releaseTitle
        ?.takeIf { it.isNotNullNorBlank() }
        ?: "No release title"
      val releaseDescription = release.releaseDescription
        ?.takeIf { it.isNotNullNorBlank() }
        ?: "No release description"

      val apkURL = findSuitableApkUrl(release.assets)
      if (apkURL == null) {
        continue
      }

      return ApkReleaseInfo(
        versionCode = versionCode,
        prerelease = prerelease,
        tagName = tagName,
        releaseTitle = releaseTitle,
        releaseDescription = releaseDescription,
        apkURL = apkURL
      )
    }

    return null
  }

  private fun findSuitableApkUrl(assets: List<Release.Asset>?): HttpUrl? {
    if (assets.isNullOrEmpty()) {
      Logger.error(TAG) { "assets is null or empty" }
      return null
    }

    val supportedAbis = Build.SUPPORTED_ABIS
    Logger.debug(TAG) { "supportedAbis: ${supportedAbis.joinToString()}" }

    val apkUrls = assets.mapNotNull { asset -> asset.url?.toHttpUrl() }
    Logger.debug(TAG) { "apkUrls: ${apkUrls.joinToString()}" }

    if (apkUrls.isEmpty()) {
      throw UpdateRequestError("No APK URL!")
    }

    var apkUrl: HttpUrl? = null

    for (abi in supportedAbis) {
      apkUrl = apkUrls.firstOrNull { apkUrl ->
        val apkFileName = apkUrl.pathSegments.last()
        return@firstOrNull apkFileName.contains(abi, ignoreCase = true)
      }

      if (apkUrl != null) {
        // Found apk for the current ABI
        break
      }
    }

    if (apkUrl == null) {
      Logger.warning(TAG) {
        "Failed to find an apk for abis: '${supportedAbis.joinToString()}', " +
          "using the last one (should be universal apk)"
      }

      apkUrl = apkUrls.last()
    }

    Logger.debug(TAG) { "Got apkUrl: '${apkUrl}'" }
    return apkUrl
  }
  
  private fun readVersionCode(release: Release): VersionCode? {
    val tagName = release.tagName
    if (tagName.isNullOrBlank()) {
      Logger.error(TAG) { "tagName is null or blank" }
      return null
    }

    val prerelease = release.prerelease
    if (prerelease == null) {
      Logger.error(TAG) { "prerelease is null" }
      return null
    }

    try {
      if (prerelease) {
        val betaVersionCode = ReleaseHelpers.calculateBetaVersionCode(tagName)
        if (betaVersionCode.versionCode <= 0L) {
          throw UpdateRequestError("Bad betaVersionCode: ${betaVersionCode}, tagName: ${tagName}")
        }

        return VersionCode.Beta(
          code = betaVersionCode.versionCode,
          buildNumber = betaVersionCode.buildNumber
        )
      } else {
        val stableVersionCode = ReleaseHelpers.calculateReleaseVersionCode(tagName)
        if (stableVersionCode <= 0L) {
          throw UpdateRequestError("Bad stableVersionCode: ${stableVersionCode}, tagName: ${tagName}")
        }

        return VersionCode.Release(
          code = stableVersionCode
        )
      }
    } catch (ignored: Exception) {
      if (prerelease) {
        throw UpdateRequestError("Tag name wasn't of the form v(major).(minor).(patch).(build)!")
      } else {
        throw UpdateRequestError("Tag name wasn't of the form v(major).(minor).(patch)!")
      }
    }
  }
  
  data class ApkReleaseInfo(
    val versionCode: VersionCode,
    val prerelease: Boolean,
    val tagName: String,
    val releaseTitle: String,
    val releaseDescription: String,
    val apkURL: HttpUrl
  )

  sealed interface VersionCode {
    val code: Long

    fun buildNumber(): Long? {
      return when (this) {
        is Beta -> buildNumber
        is Release -> null
      }
    }

    data class Beta(
      override val code: Long,
      val buildNumber: Long
    ) : VersionCode

    data class Release(
      override val code: Long,
    ) : VersionCode
  }

  @JsonClass(generateAdapter = true)
  data class Release(
    @field:Json(name = "tag_name") val tagName: String? = null,
    @field:Json(name = "name") val releaseTitle: String? = null,
    @field:Json(name = "body") val releaseDescription: String? = null,
    @field:Json(name = "prerelease") val prerelease: Boolean? = null,
    @field:Json(name = "assets") val assets: List<Asset>? = null,
  ) {
    @JsonClass(generateAdapter = true)
    data class Asset(
      @field:Json(name = "browser_download_url") val url: String? = null
    )
  }

  companion object {
    private const val TAG = "UpdateApiRequest"
  }
}