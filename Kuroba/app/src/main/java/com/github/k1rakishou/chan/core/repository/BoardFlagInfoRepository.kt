package com.github.k1rakishou.chan.core.repository

import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4SiteSettings
import com.github.k1rakishou.chan.core.usecase.LoadBoardFlagsUseCase
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.v2.settings.KurobaStringSetting
import java.util.concurrent.ConcurrentHashMap

class BoardFlagInfoRepository(
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val loadBoardFlagsUseCase: LoadBoardFlagsUseCase
) {
  private val cachedFlagInfoMap = ConcurrentHashMap<BoardDescriptor, List<LoadBoardFlagsUseCase.FlagInfo>>(64)
  private val defaultFlagInfoMap = ConcurrentHashMap<BoardDescriptor, LoadBoardFlagsUseCase.FlagInfo>(64)
  private val alreadyCheckedBoards = ConcurrentHashMap<BoardDescriptor, Unit>(64)

  suspend fun getFlagInfoList(boardDescriptor: BoardDescriptor): List<LoadBoardFlagsUseCase.FlagInfo> {
    if (cachedFlagInfoMap[boardDescriptor].isNullOrEmpty() && !alreadyCheckedBoards.contains(boardDescriptor)) {
      boardManager.awaitUntilInitialized()

      val supportsFlags = boardManager.byBoardDescriptor(boardDescriptor)?.countryFlags ?: false
      if (!supportsFlags) {
        alreadyCheckedBoards[boardDescriptor] = Unit
        return emptyList()
      }

      loadFlags(boardDescriptor)
      alreadyCheckedBoards[boardDescriptor] = Unit
    }

    return cachedFlagInfoMap[boardDescriptor]?.toList() ?: emptyList()
  }

  fun storeLastUsedFlag(
    lastUsedCountryFlagPerBoardSetting: KurobaStringSetting,
    selectedFlagInfo: LoadBoardFlagsUseCase.FlagInfo,
    currentBoardCode: String
  ) {
    // board_code:flag_code;board_code:flag_code;board_code:flag_code;etc...

    val flagMap = mutableMapOf<String, String>()
    val boardCodeFlagCodePairs = lastUsedCountryFlagPerBoardSetting.readBlocking().split(';')

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

    lastUsedCountryFlagPerBoardSetting.writeAsync(resultFlags)
  }

  fun getLastUsedFlagKey(boardDescriptor: BoardDescriptor): String? {
    val lastUsedCountryFlagPerBoardSetting = siteManager.bySiteDescriptorAndActive(boardDescriptor.siteDescriptor)
      ?.siteSettingsOrNull(Chan4SiteSettings::class.java)
      ?.lastUsedFlagPerBoard
      ?: return null

    return extractFlagCodeOrDefault(
      lastUsedCountryFlagPerBoardString = lastUsedCountryFlagPerBoardSetting.readBlocking(),
      currentBoardCode = boardDescriptor.boardCode
    )
  }

  fun getLastUsedFlagInfo(boardDescriptor: BoardDescriptor): LoadBoardFlagsUseCase.FlagInfo? {
    val lastUsedCountryFlagPerBoardSetting = siteManager.bySiteDescriptorAndActive(boardDescriptor.siteDescriptor)
      ?.siteSettingsOrNull(Chan4SiteSettings::class.java)
      ?.lastUsedFlagPerBoard
      ?: return null

    val lastUsedCountryFlagPerBoard = lastUsedCountryFlagPerBoardSetting.readBlocking()

    var lastUsedFlagInfo = getFlagInfoByFlagKeyOrNull(
      lastUsedCountryFlagPerBoard = lastUsedCountryFlagPerBoard,
      boardDescriptor = boardDescriptor
    )

    if (lastUsedFlagInfo == null) {
      lastUsedFlagInfo = getDefaultFlagInfo(boardDescriptor)
    }

    return lastUsedFlagInfo
  }

  private fun extractFlagCodeOrDefault(lastUsedCountryFlagPerBoardString: String, currentBoardCode: String): String {
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