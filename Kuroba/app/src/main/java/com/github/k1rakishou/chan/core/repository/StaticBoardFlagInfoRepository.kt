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
  }

  fun getFlagInfoList(boardDescriptor: BoardDescriptor): List<FlagInfo> {
    return flagInfoMap[boardDescriptor]?.toList() ?: emptyList()
  }

  fun getLastUsedFlagInfo(boardDescriptor: BoardDescriptor): FlagInfo? {
    val countryFlagSetting = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
      ?.getSettingBySettingId<StringSetting>(SiteSetting.SiteSettingId.CountryFlag)
      ?: return null

    val lastUsedFlagKey = countryFlagSetting.get()

    var lastUsedFlagInfo = getFlagInfoByFlagKeyOrNull(
      lastUsedFlagKey,
      boardDescriptor
    )

    if (lastUsedFlagInfo == null) {
      lastUsedFlagInfo = getDefaultFlagInfo(boardDescriptor)
    }

    return lastUsedFlagInfo
  }

  private fun getFlagInfoByFlagKeyOrNull(flagKey: String, boardDescriptor: BoardDescriptor): FlagInfo? {
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
<option value="0"> Geographic Location</option>
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
  }

}