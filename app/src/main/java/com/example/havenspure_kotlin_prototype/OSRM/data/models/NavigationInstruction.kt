package com.example.havenspure_kotlin_prototype.OSRM.data.models

import android.annotation.SuppressLint

/**
 * Represents a single navigation instruction for turn-by-turn directions.
 *
 * @property text The instruction text in German
 * @property distance Distance in meters until this instruction applies
 * @property duration Estimated duration in seconds until this instruction
 * @property type The type of instruction (turn left, right, etc.)
 * @property index Optional node index in the route where this instruction applies
 */
data class NavigationInstruction(
    val text: String,
    val distance: Double,
    val duration: Double,
    val type: NavigationInstructionType,
    val index: Int? = null
) {
    /**
     * Format the distance for display
     */
    @SuppressLint("DefaultLocale")
    fun formatDistance(): String {
        return when {
            distance < 30 -> "Jetzt"
            distance < 100 -> "In Kürze"
            distance < 1000 -> "In ${(distance / 10).toInt() * 10} Metern"
            else -> "In ${String.format("%.1f", distance / 1000)} km"
        }
    }

    /**
     * Get a formatted instruction text with distance
     */
    fun getFormattedInstruction(): String {
        return "${formatDistance()}: $text"
    }
}

/**
 * Types of navigation instructions
 */
enum class NavigationInstructionType {
    DEPART,
    ARRIVE,
    GO_STRAIGHT,
    TURN_SLIGHT_RIGHT,
    TURN_RIGHT,
    TURN_SHARP_RIGHT,
    UTURN_RIGHT,
    UTURN_LEFT,
    TURN_SHARP_LEFT,
    TURN_LEFT,
    TURN_SLIGHT_LEFT,
    ROUNDABOUT,
    EXIT_ROUNDABOUT,
    CONTINUE
}

/**
 * Extension function to convert instruction type to German text
 */
fun NavigationInstructionType.toGermanText(): String {
    return when (this) {
        NavigationInstructionType.DEPART -> "Starten Sie Ihre Route"
        NavigationInstructionType.ARRIVE -> "Sie haben Ihr Ziel erreicht"
        NavigationInstructionType.GO_STRAIGHT -> "Fahren Sie geradeaus"
        NavigationInstructionType.TURN_SLIGHT_RIGHT -> "Halten Sie sich rechts"
        NavigationInstructionType.TURN_RIGHT -> "Biegen Sie rechts ab"
        NavigationInstructionType.TURN_SHARP_RIGHT -> "Biegen Sie scharf rechts ab"
        NavigationInstructionType.UTURN_RIGHT -> "Bitte wenden Sie (rechts)"
        NavigationInstructionType.UTURN_LEFT -> "Bitte wenden Sie (links)"
        NavigationInstructionType.TURN_SHARP_LEFT -> "Biegen Sie scharf links ab"
        NavigationInstructionType.TURN_LEFT -> "Biegen Sie links ab"
        NavigationInstructionType.TURN_SLIGHT_LEFT -> "Halten Sie sich links"
        NavigationInstructionType.ROUNDABOUT -> "Befahren Sie den Kreisverkehr"
        NavigationInstructionType.EXIT_ROUNDABOUT -> "Verlassen Sie den Kreisverkehr"
        NavigationInstructionType.CONTINUE -> "Folgen Sie der Route"
    }
}