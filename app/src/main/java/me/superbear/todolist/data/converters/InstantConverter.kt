package me.superbear.todolist.data.converters

import androidx.room.TypeConverter
import kotlinx.datetime.Instant

/**
 * Room类型转换器，用于处理kotlinx.datetime.Instant类型
 */
class InstantConverter {
    
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? {
        return instant?.epochSeconds
    }
    
    @TypeConverter
    fun toInstant(epochSeconds: Long?): Instant? {
        return epochSeconds?.let { Instant.fromEpochSeconds(it) }
    }
}
