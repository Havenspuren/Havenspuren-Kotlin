package com.example.havenspure_kotlin_prototype.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.ForeignKey
import androidx.room.Embedded
import androidx.room.Index
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * Tour entity representing a complete tour with multiple locations
 */
@Parcelize
@Entity(tableName = "tours")
data class Tour(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val totalLocations: Int,
    val author: String
) : Parcelable

@Parcelize
@Entity(
    tableName = "locations",
    foreignKeys = [
        ForeignKey(
            entity = Tour::class,
            parentColumns = ["id"],
            childColumns = ["tourId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tourId")]
)
data class Location(
    @PrimaryKey val id: String,
    val tourId: String,
    val name: String,
    val order: Int,
    val latitude: Double,
    val longitude: Double,
    val audioFileName: String,
    val bubbleText: String,
    val detailText: String,
    val hasTrophy: Boolean,
    val trophyTitle: String?,
    val trophyDescription: String?,
    val trophyImageName: String?,
    val imageName: String? = null
) : Parcelable

/**
 * UserProgress entity to track user's progress through tours
 */
@Parcelize
@Entity(tableName = "user_progress")
data class UserProgress(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tourId: String,
    val lastVisitedLocationId: String,
    val completionPercentage: Float,
    val startedAt: Long,
    val lastUpdatedAt: Long
) : Parcelable

/**
 * VisitedLocation entity to track which locations have been visited
 */
@Entity(
    tableName = "visited_locations",
    foreignKeys = [
        ForeignKey(
            entity = Location::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("locationId")] // Add this line to fix the warning
)
data class VisitedLocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val locationId: String,
    val tourId: String,
    val visitedAt: Long
)

/**
 * Trophy entity representing achievements unlocked during tours
 */
@Parcelize
@Entity(tableName = "trophies")
data class Trophy(
    @PrimaryKey val id: String,
    val tourId: String,
    val locationId: String,
    val title: String,
    val description: String,
    val imageName: String,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null
) : Parcelable

/**
 * Relationship class to fetch tour with its locations
 */
@Parcelize
data class TourWithLocations(
    @Embedded val tour: Tour,
    @Relation(
        parentColumn = "id",
        entityColumn = "tourId"
    )
    val locations: @RawValue List<Location>
) : Parcelable

/**
 * Relationship class to fetch user progress with visited locations
 */
data class UserProgressWithVisitedLocations(
    @Embedded val userProgress: UserProgress,
    @Relation(
        parentColumn = "tourId",
        entityColumn = "tourId"
    )
    val visitedLocations: List<VisitedLocation>
)

/**
 * Relationship class for tour with progress
 */
@Parcelize
data class TourWithProgress(
    @Embedded val tour: Tour,
    @Relation(
        parentColumn = "id",
        entityColumn = "tourId"
    )
    val userProgress: @RawValue UserProgress?
) : Parcelable