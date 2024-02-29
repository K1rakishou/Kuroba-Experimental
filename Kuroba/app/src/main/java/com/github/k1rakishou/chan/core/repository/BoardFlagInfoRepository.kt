package com.github.k1rakishou.chan.core.repository

import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.usecase.LoadBoardFlagsUseCase
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.prefs.StringSetting
import java.util.concurrent.ConcurrentHashMap

class BoardFlagInfoRepository(
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val loadBoardFlagsUseCase: LoadBoardFlagsUseCase
) {
  private val cachedFlagInfoMap = ConcurrentHashMap<BoardDescriptor, List<LoadBoardFlagsUseCase.FlagInfo>>()
  private val defaultFlagInfoMap = ConcurrentHashMap<BoardDescriptor, LoadBoardFlagsUseCase.FlagInfo>()

  suspend fun cached(boardDescriptor: BoardDescriptor): Boolean {
    return cachedFlagInfoMap[boardDescriptor]?.isNotEmpty() == true
  }

  suspend fun getFlagInfoList(boardDescriptor: BoardDescriptor): List<LoadBoardFlagsUseCase.FlagInfo> {
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
    selectedFlagInfo: LoadBoardFlagsUseCase.FlagInfo,
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

  fun getLastUsedFlagInfo(boardDescriptor: BoardDescriptor): LoadBoardFlagsUseCase.FlagInfo? {
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
    val flags = loadBoardFlagsUseCase.await(boardDescriptor)
      .onError { error -> Logger.error(TAG) { "loadFlags(${boardDescriptor}) error: ${error}" } }
      .valueOrNull()

    if (flags.isNullOrEmpty()) {
      return false
    }

    cachedFlagInfoMap[boardDescriptor] = flags

    if (!defaultFlagInfoMap.containsKey(boardDescriptor)) {
      var defaultFlag = flags
        .firstOrNull { flagInfo -> flagInfo.flagKey == "0" }

      if (defaultFlag == null) {
        defaultFlag = LoadBoardFlagsUseCase.FlagInfo("0", "No flag")
      }

      defaultFlagInfoMap[boardDescriptor] = defaultFlag
    }

    return true
  }

  private fun getFlagInfoByFlagKeyOrNull(
    lastUsedCountryFlagPerBoard: String,
    boardDescriptor: BoardDescriptor
  ): LoadBoardFlagsUseCase.FlagInfo? {
    val flagKey = extractFlagCodeOrDefault(lastUsedCountryFlagPerBoard, boardDescriptor.boardCode)

    val flagInfoList = cachedFlagInfoMap[boardDescriptor]
      ?: return null

    return flagInfoList.firstOrNull { flagInfo -> flagInfo.flagKey == flagKey }
  }

  private fun getDefaultFlagInfo(boardDescriptor: BoardDescriptor): LoadBoardFlagsUseCase.FlagInfo? {
    return defaultFlagInfoMap[boardDescriptor]
  }

  companion object {
    private const val TAG = "BoardFlagInfoRepository"
  }

}