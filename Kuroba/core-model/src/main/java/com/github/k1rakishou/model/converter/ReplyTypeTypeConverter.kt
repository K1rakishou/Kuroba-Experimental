package com.github.k1rakishou.model.converter

import androidx.room.TypeConverter
import com.github.k1rakishou.model.entity.chan.post.ChanPostReplyEntity

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