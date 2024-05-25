package com.github.k1rakishou.model.data.post

sealed interface ChanPostIcon {

  data class CountryFlag(
    val flagId: String,
    val flagName: String?
  ) : ChanPostIcon

  data class CustomFlag(
    val type: FlagType,
    val flagId: String,
    val flagName: String?
  ) : ChanPostIcon {

    sealed interface FlagType {
      val prefix: String

      data object CustomCountryFlag : FlagType {
        override val prefix: String = "_t"
      }

      data object CustomBoardFlag : FlagType {
        override val prefix: String = "_t"
      }
    }
  }

  data class BoardFlag(
    val flagId: String,
    val flagName: String?
  ) : ChanPostIcon

  data class Since4Pass(
    val year: Int
  ) : ChanPostIcon

  data class DvachFlag(
    val title: String
  ) : ChanPostIcon

}