package com.example.havenspure_kotlin_prototype.Utils

import android.content.Context
import android.media.MediaPlayer

/**
 * Utility class for handling audio playback
 */
class AudioUtils(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var currentAudioFile: String? = null

    /**
     * Play audio file from assets
     */
    fun playAudio(fileName: String, onCompletion: () -> Unit = {}) {
        // Stop current playback if any
        stopAudio()

        try {
            mediaPlayer = MediaPlayer().apply {
                val assetFileDescriptor = context.assets.openFd("audio/$fileName")
                setDataSource(
                    assetFileDescriptor.fileDescriptor,
                    assetFileDescriptor.startOffset,
                    assetFileDescriptor.length
                )
                assetFileDescriptor.close()

                setOnPreparedListener {
                    start()
                    this@AudioUtils.isPlaying = true
                    currentAudioFile = fileName
                }

                setOnCompletionListener {
                    this@AudioUtils.isPlaying = false
                    currentAudioFile = null
                    onCompletion()
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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