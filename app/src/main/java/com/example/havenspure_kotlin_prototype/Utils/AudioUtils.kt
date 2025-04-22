package com.example.havenspure_kotlin_prototype.Utils

import android.content.Context
import android.media.MediaPlayer
import android.util.Log

/**
 * Utility class for handling audio playback
 */
class AudioUtils(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var currentAudioFile: String? = null

    /**
     * Play audio file from assets with a full path
     * @param assetPath Full path to the audio file in assets
     * @param onCompletion Callback when audio playback completes
     */
    fun playAudio(assetPath: String, onCompletion: () -> Unit = {}) {
        // Stop current playback if any
        stopAudio()

        try {
            mediaPlayer = MediaPlayer().apply {
                val assetFileDescriptor = context.assets.openFd(assetPath)
                setDataSource(
                    assetFileDescriptor.fileDescriptor,
                    assetFileDescriptor.startOffset,
                    assetFileDescriptor.length
                )
                assetFileDescriptor.close()

                setOnPreparedListener {
                    start()
                    this@AudioUtils.isPlaying = true
                    currentAudioFile = assetPath
                }

                setOnCompletionListener {
                    this@AudioUtils.isPlaying = false
                    currentAudioFile = null
                    onCompletion()
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("AudioUtils", "Error playing audio file: $assetPath", e)
            onCompletion() // Call completion callback even on error
        }
    }

    /**
     * Play audio for a specific tour
     * @param tourId The tour identifier
     * @param fileName The audio file name
     * @param onCompletion Callback when audio playback completes
     */
    fun playTourAudio(tourId: String, fileName: String, onCompletion: () -> Unit = {}) {
        val cleanTourId = tourId.trim().let {
            if (it.startsWith("tour_")) it else "tour_$it"
        }

        playAudio("$cleanTourId/audio/$fileName", onCompletion)
    }

    /**
     * Stop current audio playback
     */
    private fun stopAudio() {
        mediaPlayer?.apply {
            if (isPlaying()) {
                stop()
            }
            reset()
            release()
        }
        mediaPlayer = null
        isPlaying = false
        currentAudioFile = null
    }

    /**
     * Check if audio is currently playing
     */
    fun isAudioPlaying(): Boolean = isPlaying

    /**
     * Get current playing audio file name
     */
    fun getCurrentAudioFile(): String? = currentAudioFile

    /**
     * Clean up resources
     */
    fun release() {
        stopAudio()
    }
}