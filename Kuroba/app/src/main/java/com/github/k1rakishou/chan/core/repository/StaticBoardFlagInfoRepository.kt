package com.github.k1rakishou.chan.core.repository

import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.prefs.StringSetting
import java.util.regex.Pattern

class StaticBoardFlagInfoRepository(
  private val siteManager: SiteManager
) {
  private val flagInfoMap = mutableMapOf<BoardDescriptor, List<FlagInfo>>()

  init {
    extractFlags(CHAN4_POL_FLAGS, BoardDescriptor.Companion.create(Chan4.SITE_DESCRIPTOR, "pol"))
    extractFlags(CHAN4_MLP_FLAGS, BoardDescriptor.Companion.create(Chan4.SITE_DESCRIPTOR, "mlp"))
  }

  fun getFlagInfoList(boardDescriptor: BoardDescriptor): List<FlagInfo> {
    return flagInfoMap[boardDescriptor]?.toList() ?: emptyList()
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

      if (boardCode == currentBoardCode) {
        resultFlagCode = flagCode
        break
      }
    }

    return resultFlagCode
  }

  fun getLastUsedFlagInfo(boardDescriptor: BoardDescriptor): FlagInfo? {
    val lastUsedCountryFlagPerBoardSetting = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
      ?.getSettingBySettingId<StringSetting>(SiteSetting.SiteSettingId.LastUsedCountryFlagPerBoard)
      ?: return null

    val lastUsedCountryFlagPerBoard = lastUsedCountryFlagPerBoardSetting.get()

    var lastUsedFlagInfo = getFlagInfoByFlagKeyOrNull(
      lastUsedCountryFlagPerBoard,
      boardDescriptor
    )

    if (lastUsedFlagInfo == null) {
      lastUsedFlagInfo = getDefaultFlagInfo(boardDescriptor)
    }

    return lastUsedFlagInfo
  }

  private fun getFlagInfoByFlagKeyOrNull(
    lastUsedCountryFlagPerBoard: String,
    boardDescriptor: BoardDescriptor
  ): FlagInfo? {
    val flagKey = extractFlagCodeOrDefault(lastUsedCountryFlagPerBoard, boardDescriptor.boardCode)

    val flagInfoList = flagInfoMap[boardDescriptor]
      ?: return null

    return flagInfoList.firstOrNull { flagInfo -> flagInfo.flagKey == flagKey }
  }

  private fun getDefaultFlagInfo(boardDescriptor: BoardDescriptor): FlagInfo? {
    return flagInfoMap[boardDescriptor]?.firstOrNull()
  }

  private fun extractFlags(input: String, boardDescriptor: BoardDescriptor) {
    val matcher = FLAG_PATTERN.matcher(input)
    val listOfFlags = mutableListOf<FlagInfo>()

    while (matcher.find()) {
      val flagKey = matcher.groupOrNull(1)
        ?: continue
      val flagDescription = matcher.groupOrNull(2)
        ?: continue

      listOfFlags += FlagInfo(flagKey, flagDescription)
    }

    flagInfoMap[boardDescriptor] = listOfFlags
  }

  data class FlagInfo(
    val flagKey: String,
    val flagDescription: String
  )

  companion object {
    private val FLAG_PATTERN = Pattern.compile("<option value=\"(.*)\">(.*)<\\/option>")

    private const val CHAN4_POL_FLAGS = """
<option value="0">Geographic Location</option>
<option value="AC">Anarcho-Capitalist</option>
<option value="AN">Anarchist</option>
<option value="BL">Black Nationalist</option>
<option value="CF">Confederate</option>
<option value="CM">Communist</option>
<option value="CT">Catalonia</option>
<option value="DM">Democrat</option>
<option value="EU">European</option>
<option value="FC">Fascist</option>
<option value="GN">Gadsden</option>
<option value="GY">Gay</option>
<option value="JH">Jihadi</option>
<option value="KN">Kekistani</option>
<option value="MF">Muslim</option>
<option value="NB">National Bolshevik</option>
<option value="NZ">Nazi</option>
<option value="PC">Hippie</option>
<option value="PR">Pirate</option>
<option value="RE">Republican</option>
<option value="TM">Templar</option>
<option value="TR">Tree Hugger</option>
<option value="UN">United Nations</option>
<option value="WP">White Supremacist</option>
  """

    private const val CHAN4_MLP_FLAGS = """
<option value="0">None</option>
<option value="4CC">4cc /mlp/</option>
<option value="ADA">Adagio Dazzle</option>
<option value="AN">Anon</option>
<option value="ANF">Anonfilly</option>
<option value="APB">Apple Bloom</option>
<option value="AJ">Applejack</option>
<option value="AB">Aria Blaze</option>
<option value="AU">Autumn Blaze</option>
<option value="BB">Bon Bon</option>
<option value="BM">Big Mac</option>
<option value="BP">Berry Punch</option>
<option value="BS">Babs Seed</option>
<option value="CL">Changeling</option>
<option value="CO">Coco Pommel</option>
<option value="CG">Cozy Glow</option>
<option value="CHE">Cheerilee</option>
<option value="CB">Cherry Berry</option>
<option value="DAY">Daybreaker</option>
<option value="DD">Daring Do</option>
<option value="DER">Derpy Hooves</option>
<option value="DT">Diamond Tiara</option>
<option value="DIS">Discord</option>
<option value="EQA">EqG Applejack</option>
<option value="EQF">EqG Fluttershy</option>
<option value="EQP">EqG Pinkie Pie</option>
<option value="EQR">EqG Rainbow Dash</option>
<option value="EQT">EqG Trixie</option>
<option value="EQI">EqG Twilight Sparkle</option>
<option value="EQS">EqG Sunset Shimmer</option>
<option value="ERA">EqG Rarity</option>
<option value="FAU">Fausticorn</option>
<option value="FLE">Fleur de lis</option>
<option value="FL">Fluttershy</option>
<option value="GI">Gilda</option>
<option value="IZ">G5 Izzy Moonbow</option>
<option value="LI">Limestone</option>
<option value="LT">Lord Tirek</option>
<option value="LY">Lyra Heartstrings</option>
<option value="MA">Marble</option>
<option value="MAU">Maud</option>
<option value="MIN">Minuette</option>
<option value="NI">Nightmare Moon</option>
<option value="NUR">Nurse Redheart</option>
<option value="OCT">Octavia</option>
<option value="PAR">Parasprite</option>
<option value="PC">Princess Cadance</option>
<option value="PCE">Princess Celestia</option>
<option value="PI">Pinkie Pie</option>
<option value="PLU">Princess Luna</option>
<option value="PM">Pinkamena</option>
<option value="PP">G5 Pipp Petals</option>
<option value="QC">Queen Chrysalis</option>
<option value="RAR">Rarity</option>
<option value="RD">Rainbow Dash</option>
<option value="RLU">Roseluck</option>
<option value="S1L">S1 Luna</option>
<option value="SCO">Scootaloo</option>
<option value="SHI">Shining Armor</option>
<option value="SIL">Silver Spoon</option>
<option value="SON">Sonata Dusk</option>
<option value="SP">Spike</option>
<option value="SPI">Spitfire</option>
<option value="SS">G5 Sunny Starscout</option>
<option value="STA">Star Dancer</option>
<option value="STL">Starlight Glimmer</option>
<option value="SUN">Sunburst</option>
<option value="SUS">Sunset Shimmer</option>
<option value="SWB">Sweetie Belle</option>
<option value="TFA">TFH Arizona</option>
<option value="TFO">TFH Oleander</option>
<option value="TFP">TFH Paprika</option>
<option value="TFS">TFH Shanty</option>
<option value="TFT">TFH Tianhuo</option>
<option value="TFV">TFH Velvet</option>
<option value="TP">TFH Pom</option>
<option value="TS">Tempest Shadow</option>
<option value="TWI">Twilight Sparkle</option>
<option value="TX">Trixie</option>
<option value="VS">Vinyl Scratch</option>
<option value="ZE">Zecora</option>
    """
  }

}