package com.example.havenspure_kotlin_prototype.data.local

import com.havenspure.data.local.LocationDao
import com.havenspure.data.local.TourDao
import com.havenspure.data.local.TrophyDao
import com.havenspure.data.local.UserProgressDao
import com.havenspure.data.local.VisitedLocationDao

/**
 * Room database for the application
 */
abstract class AppDatabase : androidx.room.RoomDatabase() {
    abstract fun tourDao(): TourDao
    abstract fun locationDao(): LocationDao
    abstract fun userProgressDao(): UserProgressDao
    abstract fun visitedLocationDao(): VisitedLocationDao
    abstract fun trophyDao(): TrophyDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Define your migration steps here if needed
            }
        }
    }
}