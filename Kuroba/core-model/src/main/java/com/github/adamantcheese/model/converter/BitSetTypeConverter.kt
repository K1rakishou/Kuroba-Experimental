package com.github.adamantcheese.model.converter

import androidx.room.TypeConverter
import java.util.*

class BitSetTypeConverter {

  @TypeConverter
  fun toBitSet(value: Long): BitSet {
    return BitSet.valueOf(longArrayOf(value))
  }

  @TypeConverter
  fun fromBitSet(bitSet: BitSet): Long {
    return bitSet.toLongArray().first()
  }

}