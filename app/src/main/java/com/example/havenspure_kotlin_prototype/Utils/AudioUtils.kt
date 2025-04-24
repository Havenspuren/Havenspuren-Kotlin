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
    private var isPaused = false
    private var currentAudioFile: String? = null
    private var audioDuration: Int = 0
    private var lastPosition: Int = 0

    /**
     * Play audio file from assets with a full path
     * @param assetPath Full path to the audio file in assets
     * @param onCompletion Callback when audio playback completes
     */
    fun playAudio(assetPath: String, onCompletion: () -> Unit = {}) {
        // If we're resuming the same audio file from paused state
        if (isPaused && assetPath == currentAudioFile && mediaPlayer != null) {
            resumeAudio()
            return
        }

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
                    audioDuration = duration
                    start()
                    this@AudioUtils.isPlaying = true
                    isPaused = false
                    currentAudioFile = assetPath
                }

                setOnCompletionListener {
                    this@AudioUtils.isPlaying = false
                    isPaused = false
                    currentAudioFile = null
                    audioDuration = 0
                    lastPosition = 0
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
     * Pause current audio playback
     */
    fun pauseAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                lastPosition = it.currentPosition
                it.pause()
                isPlaying = false
                isPaused = true
            }
        }
    }

    /**
     * Resume paused audio playback
     */
    fun resumeAudio() {
        mediaPlayer?.let {
            if (!it.isPlaying && isPaused) {
                it.start()
                isPlaying = true
                isPaused = false
            }
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
    fun stopAudio() {
        mediaPlayer?.apply {
            if (isPlaying()) {
                stop()
            }
            reset()
            release()
        }
        mediaPlayer = null
        isPlaying = false
        isPaused = false
        currentAudioFile = null
        audioDuration = 0
        lastPosition = 0
    }

    /**
     * Check if audio is currently playing
     */
    fun isAudioPlaying(): Boolean = isPlaying

    /**
     * Check if audio is currently paused
     */
    fun isAudioPaused(): Boolean = isPaused

    /**
     * Get current playing audio file name
     */
    fun getCurrentAudioFile(): String? = currentAudioFile

    /**
     * Get duration of current audio (in milliseconds)
     */
    fun getAudioDuration(): Int = audioDuration

    /**
     * Seek to position in current audio (in milliseconds)
     */
    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    /**
     * Get current playback position (in milliseconds)
     */
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: lastPosition

    /**
     * Clean up resources
     */
    fun release() {
        stopAudio()
    }
}