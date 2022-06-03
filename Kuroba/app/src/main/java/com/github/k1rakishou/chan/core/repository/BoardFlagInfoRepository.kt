package com.github.k1rakishou.chan.core.repository

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.common.suspendConvertIntoJsoupDocument
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.prefs.StringSetting
import okhttp3.Request
import org.jsoup.nodes.Element
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class BoardFlagInfoRepository(
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val proxiedOkHttpClient: ProxiedOkHttpClient
) {
  private val cachedFlagInfoMap = ConcurrentHashMap<BoardDescriptor, List<FlagInfo>>()
  private val defaultFlagInfoMap = ConcurrentHashMap<BoardDescriptor, FlagInfo>()

  suspend fun cached(boardDescriptor: BoardDescriptor): Boolean {
    return cachedFlagInfoMap[boardDescriptor]?.isNotEmpty() == true
  }

  suspend fun getFlagInfoList(boardDescriptor: BoardDescriptor): List<FlagInfo> {
    if (cachedFlagInfoMap[boardDescriptor].isNullOrEmpty()) {
      boardManager.awaitUntilInitialized()

      val supportsFlags = boardManager.byBoardDescriptor(boardDescriptor)?.countryFlags ?: false
      if (!supportsFlags) {
        return emptyList()
      }

      loadFlags(boardDescriptor)
    }

    return cachedFlagInfoMap[boardDescriptor]?.toList() ?: emptyList()
  }

  fun storeLastUsedFlag(
    lastUsedCountryFlagPerBoardSetting: StringSetting,
    selectedFlagInfo: FlagInfo,
    currentBoardCode: String
  ) {
    // board_code:flag_code;board_code:flag_code;board_code:flag_code;etc...

    val flagMap = mutableMapOf<String, String>()
    val boardCodeFlagCodePairs = lastUsedCountryFlagPerBoardSetting.get().split(';')

    for (boardCodeFlagCodePair in boardCodeFlagCodePairs) {
      val splitPair = boardCodeFlagCodePair.split(':')
      if (splitPair.size != 2) {
        continue
      }

      val boardCode = splitPair[0]
      val flagCode = splitPair[1]

      flagMap[boardCode] = flagCode
    }

    flagMap[currentBoardCode] = selectedFlagInfo.flagKey

    val resultFlags = buildString {
      var index = 1

      flagMap.entries.forEach { (boardCode, flagKey) ->
        append("${boardCode}:${flagKey}")

        if (index != flagMap.size) {
          append(";")
        }

        ++index
      }
    }

    lastUsedCountryFlagPerBoardSetting.set(resultFlags)
  }

  fun extractFlagCodeOrDefault(lastUsedCountryFlagPerBoardString: String, currentBoardCode: String): String {
    // board_code:flag_code;board_code:flag_code;board_code:flag_code;etc...

    val boardCodeFlagCodePairs = lastUsedCountryFlagPerBoardString.split(';')
    var resultFlagCode = "0"

    for (boardCodeFlagCodePair in boardCodeFlagCodePairs) {
      val splitPair = boardCodeFlagCodePair.split(':')
      if (splitPair.size != 2) {
        continue
      }

      val boardCode = splitPair[0]
      val flagCode = splitPair[1]

      if (boardCode.equals(currentBoardCode, ignoreCase = true)) {
        resultFlagCode = flagCode
        break
      }
    }

    return resultFlagCode
  }

  fun getLastUsedFlagKey(boardDescriptor: BoardDescriptor): String? {
    val lastUsedCountryFlagPerBoardSetting = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
      ?.getSettingBySettingId<StringSetting>(SiteSetting.SiteSettingId.LastUsedCountryFlagPerBoard)
      ?: return null

    return extractFlagCodeOrDefault(
      lastUsedCountryFlagPerBoardString = lastUsedCountryFlagPerBoardSetting.get(),
      currentBoardCode = boardDescriptor.boardCode
    )
  }

  fun getLastUsedFlagInfo(boardDescriptor: BoardDescriptor): FlagInfo? {
    val lastUsedCountryFlagPerBoardSetting = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
      ?.getSettingBySettingId<StringSetting>(SiteSetting.SiteSettingId.LastUsedCountryFlagPerBoard)
      ?: return null

    val lastUsedCountryFlagPerBoard = lastUsedCountryFlagPerBoardSetting.get()

    var lastUsedFlagInfo = getFlagInfoByFlagKeyOrNull(
      lastUsedCountryFlagPerBoard = lastUsedCountryFlagPerBoard,
      boardDescriptor = boardDescriptor
    )

    if (lastUsedFlagInfo == null) {
      lastUsedFlagInfo = getDefaultFlagInfo(boardDescriptor)
    }

    return lastUsedFlagInfo
  }

  private suspend fun loadFlags(boardDescriptor: BoardDescriptor): Boolean {
    val url = "https://boards.4chan.org/${boardDescriptor.boardCode}/"

    val request = Request.Builder()
      .get()
      .url(url)
      .build()

    val result = proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsoupDocument(request)

    val document = if (result is ModularResult.Error) {
      return false
    } else {
      (result as ModularResult.Value).value
    }

    val flagSelector = document.selectFirst("select[class=flagSelector]")
    if (flagSelector == null) {
      return false
    }

    val flags = mutableListOf<FlagInfo>()

    flagSelector.childNodes().forEach { node ->
      if (node !is Element) {
        return@forEach
      }

      if (!node.tagName().equals("option", ignoreCase = true)) {
        return@forEach
      }

      val matcher = FLAG_PATTERN.matcher(node.toString())
      if (!matcher.find()) {
        return@forEach
      }

      val flagKey = matcher.groupOrNull(1) ?: return@forEach
      val flagDescription = matcher.groupOrNull(2) ?: return@forEach

      flags += FlagInfo(
        flagKey = flagKey,
        flagDescription = flagDescription
      )
    }

    if (flags.isEmpty()) {
      return false
    }

    cachedFlagInfoMap[boardDescriptor] = flags

    if (!defaultFlagInfoMap.containsKey(boardDescriptor)) {
      var defaultFlag = flags
        .firstOrNull { flagInfo -> flagInfo.flagKey == "0" }

      if (defaultFlag == null) {
        defaultFlag = FlagInfo("0", "No flag")
      }

      defaultFlagInfoMap[boardDescriptor] = defaultFlag
    }

    return true
  }

  private fun getFlagInfoByFlagKeyOrNull(
    lastUsedCountryFlagPerBoard: String,
    boardDescriptor: BoardDescriptor
  ): FlagInfo? {
    val flagKey = extractFlagCodeOrDefault(lastUsedCountryFlagPerBoard, boardDescriptor.boardCode)

    val flagInfoList = cachedFlagInfoMap[boardDescriptor]
      ?: return null

    return flagInfoList.firstOrNull { flagInfo -> flagInfo.flagKey == flagKey }
  }

  private fun getDefaultFlagInfo(boardDescriptor: BoardDescriptor): FlagInfo? {
    return defaultFlagInfoMap[boardDescriptor]
  }

  data class FlagInfo(
    val flagKey: String,
    val flagDescription: String
  )

  companion object {
    private val FLAG_PATTERN = Pattern.compile("<option value=\"(.*)\">(.*)<\\/option>")
  }

}