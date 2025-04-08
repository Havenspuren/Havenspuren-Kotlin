package com.havenspure.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.example.havenspure_kotlin_prototype.data.model.Location
import com.example.havenspure_kotlin_prototype.data.model.Tour
import com.example.havenspure_kotlin_prototype.data.model.TourWithLocations
import com.example.havenspure_kotlin_prototype.data.model.TourWithProgress
import com.example.havenspure_kotlin_prototype.data.model.Trophy
import com.example.havenspure_kotlin_prototype.data.model.UserProgress
import com.example.havenspure_kotlin_prototype.data.model.UserProgressWithVisitedLocations
import com.example.havenspure_kotlin_prototype.data.model.VisitedLocation

@Database(
    entities = [
        Tour::class,
        Location::class,
        UserProgress::class,
        VisitedLocation::class,
        Trophy::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HavenspurenDatabase : RoomDatabase() {

    abstract fun tourDao(): TourDao
    abstract fun locationDao(): LocationDao
    abstract fun userProgressDao(): UserProgressDao
    abstract fun visitedLocationDao(): VisitedLocationDao
    abstract fun trophyDao(): TrophyDao

    companion object {
        @Volatile
        private var INSTANCE: HavenspurenDatabase? = null

        fun getDatabase(context: Context): HavenspurenDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HavenspurenDatabase::class.java,
                    "havenspuren_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// DAOs for accessing the database
@Dao
interface TourDao {
    @Query("SELECT * FROM tours")
    suspend fun getAllTours(): List<Tour>

    @Transaction
    @Query("SELECT * FROM tours WHERE id = :tourId")
    suspend fun getTourWithLocations(tourId: String): TourWithLocations

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTour(tour: Tour)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTours(tours: List<Tour>)

    @Transaction
    @Query("SELECT * FROM tours")
    suspend fun getToursWithProgress(): List<TourWithProgress>

    @Query("DELETE FROM tours")
    suspend fun deleteAllTours()
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations WHERE tourId = :tourId ORDER BY `order`")
    suspend fun getLocationsForTour(tourId: String): List<Location>

    @Query("SELECT * FROM locations WHERE id = :locationId")
    suspend fun getLocationById(locationId: String): Location

    @Query("SELECT * FROM locations WHERE tourId = :tourId AND `order` = :order")
    suspend fun getLocationByOrder(tourId: String, order: Int): Location?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: Location)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocations(locations: List<Location>)

    @Query("DELETE FROM locations WHERE tourId = :tourId")
    suspend fun deleteLocationsForTour(tourId: String)
}

@Dao
interface UserProgressDao {
    @Query("SELECT * FROM user_progress WHERE tourId = :tourId")
    suspend fun getProgressForTour(tourId: String): UserProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProgress(userProgress: UserProgress): Long

    @Transaction
    @Query("SELECT * FROM user_progress WHERE tourId = :tourId")
    suspend fun getProgressWithVisitedLocations(tourId: String): UserProgressWithVisitedLocations?

    @Query("UPDATE user_progress SET completionPercentage = :percentage, lastVisitedLocationId = :locationId, lastUpdatedAt = :timestamp WHERE tourId = :tourId")
    suspend fun updateProgress(tourId: String, locationId: String, percentage: Float, timestamp: Long)
}

@Dao
interface VisitedLocationDao {
    @Query("SELECT * FROM visited_locations WHERE tourId = :tourId")
    suspend fun getVisitedLocationsForTour(tourId: String): List<VisitedLocation>

    @Query("SELECT COUNT(*) FROM visited_locations WHERE tourId = :tourId")
    suspend fun getVisitedLocationCountForTour(tourId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisitedLocation(visitedLocation: VisitedLocation): Long

    @Query("SELECT EXISTS(SELECT 1 FROM visited_locations WHERE locationId = :locationId)")
    suspend fun isLocationVisited(locationId: String): Boolean
}

@Dao
interface TrophyDao {
    @Query("SELECT * FROM trophies WHERE tourId = :tourId")
    suspend fun getTrophiesForTour(tourId: String): List<Trophy>

    @Query("SELECT * FROM trophies WHERE isUnlocked = 1")
    suspend fun getUnlockedTrophies(): List<Trophy>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrophy(trophy: Trophy)

    @Query("UPDATE trophies SET isUnlocked = 1, unlockedAt = :timestamp WHERE id = :trophyId")
    suspend fun unlockTrophy(trophyId: String, timestamp: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM trophies WHERE locationId = :locationId AND isUnlocked = 1)")
    suspend fun isTrophyUnlocked(locationId: String): Boolean
}