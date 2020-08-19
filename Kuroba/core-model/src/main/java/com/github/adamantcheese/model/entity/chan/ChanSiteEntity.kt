package com.github.adamantcheese.model.entity.chan

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.github.adamantcheese.model.data.misc.KeyValueSettings

@Entity(
  tableName = ChanSiteEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = ChanSiteIdEntity::class,
      parentColumns = [ChanSiteIdEntity.SITE_NAME_COLUMN_NAME],
      childColumns = [ChanSiteEntity.OWNER_CHAN_SITE_NAME_COLUMN_NAME],
      onDelete = ForeignKey.CASCADE,
      onUpdate = ForeignKey.CASCADE
    )
  ]
)
data class ChanSiteEntity(
  @PrimaryKey(autoGenerate = false)
  @ColumnInfo(name = OWNER_CHAN_SITE_NAME_COLUMN_NAME)
  val ownerChanSiteName: String,
  @ColumnInfo(name = SITE_ACTIVE_COLUMN_NAME)
  val siteActive: Boolean,
  @ColumnInfo(name = SITE_ORDER_COLUMN_NAME)
  val siteOrder: Int,
  @ColumnInfo(name = USER_SETTINGS_COLUMN_NAME)
  val userSettings: KeyValueSettings?
) {

  companion object {
    const val TABLE_NAME = "chan_site"

    const val OWNER_CHAN_SITE_NAME_COLUMN_NAME = "owner_chan_site_name"
    const val SITE_ACTIVE_COLUMN_NAME = "site_active"
    const val SITE_ORDER_COLUMN_NAME = "site_order"
    const val USER_SETTINGS_COLUMN_NAME = "user_settings"
  }

}