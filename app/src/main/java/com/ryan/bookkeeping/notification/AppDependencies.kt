package com.ryan.bookkeeping.notification

import android.content.Context
import androidx.room.Room
import com.ryan.bookkeeping.data.AppDatabase
import com.ryan.bookkeeping.data.TransactionRepository
import com.ryan.bookkeeping.domain.TransactionParser

object AppDependencies {
    @Volatile
    private var database: AppDatabase? = null

    @Volatile
    private var repository: TransactionRepository? = null

    val parser: TransactionParser by lazy { TransactionParser() }

    fun repository(context: Context): TransactionRepository {
        return repository ?: synchronized(this) {
            repository ?: TransactionRepository(database(context).transactionDao()).also {
                repository = it
            }
        }
    }

    private fun database(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "bookkeeping.db",
            ).fallbackToDestructiveMigration().build().also {
                database = it
            }
        }
    }
}
