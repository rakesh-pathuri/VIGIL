package com.vigil.security.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScanRecordEntity::class], version = 1, exportSchema = false)
abstract class VigilDatabase : RoomDatabase() {

    abstract fun scanRecordDao(): ScanRecordDao

    companion object {
        @Volatile
        private var INSTANCE: VigilDatabase? = null

        fun getInstance(context: Context): VigilDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VigilDatabase::class.java,
                    "vigil_security.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
