package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NoteEntity::class, CommandCacheEntity::class, CustomVoiceMappingEntity::class],
    version = 3,
    exportSchema = false
)
abstract class OganessonDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun commandCacheDao(): CommandCacheDao
    abstract fun customVoiceMappingDao(): CustomVoiceMappingDao

    companion object {
        @Volatile
        private var INSTANCE: OganessonDatabase? = null

        fun getDatabase(context: Context): OganessonDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OganessonDatabase::class.java,
                    "oganesson_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
