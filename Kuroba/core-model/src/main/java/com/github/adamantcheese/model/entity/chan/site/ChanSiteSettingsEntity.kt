package com.github.adamantcheese.model.entity.chan.site

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.github.adamantcheese.json.JsonSettings

@Entity(
  tableName = ChanSiteSettingsEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = ChanSiteIdEntity::class,
      parentColumns = [ChanSiteIdEntity.SITE_NAME_COLUMN_NAME],
      childColumns = [ChanSiteSettingsEntity.OWNER_CHAN_SITE_NAME_COLUMN_NAME],
      onDelete = ForeignKey.CASCADE,
      onUpdate = ForeignKey.CASCADE
    )
  ]
)
data class ChanSiteSettingsEntity(
  @PrimaryKey(autoGenerate = false)
  @ColumnInfo(name = OWNER_CHAN_SITE_NAME_COLUMN_NAME)
  val ownerChanSiteName: String,
  @ColumnInfo(name = USER_SETTINGS_COLUMN_NAME)
  val userSettings: JsonSettings?
) {

  companion object {
    const val TABLE_NAME = "chan_site_settings"

    const val OWNER_CHAN_SITE_NAME_COLUMN_NAME = "owner_chan_site_name"
    const val USER_SETTINGS_COLUMN_NAME = "user_settings"
  }
}