package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PhotoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KeepsakeDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile
        private var INSTANCE: KeepsakeDatabase? = null

        fun getInstance(context: Context): KeepsakeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KeepsakeDatabase::class.java,
                    "keepsake_photos.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
