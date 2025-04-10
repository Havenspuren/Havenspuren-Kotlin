package com.example.havenspure_kotlin_prototype.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.havenspure_kotlin_prototype.data.model.*
import com.havenspure.data.local.*

@Database(
    entities = [
        Tour::class,
        Location::class,
        UserProgress::class,
        VisitedLocation::class,
        Trophy::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tourDao(): TourDao
    abstract fun locationDao(): LocationDao
    abstract fun userProgressDao(): UserProgressDao
    abstract fun visitedLocationDao(): VisitedLocationDao
    abstract fun trophyDao(): TrophyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "havenspure_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Define migration steps if needed
                // Example:
                // database.execSQL("ALTER TABLE tours ADD COLUMN new_column TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}