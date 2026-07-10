package com.ryan.bookkeeping.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.ryan.bookkeeping.domain.TransactionDirection

@Database(
    entities = [TransactionEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(TransactionDirectionConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}

class TransactionDirectionConverter {
    @TypeConverter
    fun fromDirection(direction: TransactionDirection): String = direction.name

    @TypeConverter
    fun toDirection(value: String): TransactionDirection = TransactionDirection.valueOf(value)
}
