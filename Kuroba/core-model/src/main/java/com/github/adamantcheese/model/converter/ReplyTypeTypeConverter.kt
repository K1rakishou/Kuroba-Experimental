package com.github.adamantcheese.model.converter

import androidx.room.TypeConverter
import com.github.adamantcheese.model.entity.chan.ChanPostReplyEntity

class ReplyTypeTypeConverter {

  @TypeConverter
  fun toReplyType(value: Int): ChanPostReplyEntity.ReplyType {
    return ChanPostReplyEntity.ReplyType.fromValue(value)
  }

  @TypeConverter
  fun fromReplyType(replyType: ChanPostReplyEntity.ReplyType): Int {
    return replyType.value
  }

}