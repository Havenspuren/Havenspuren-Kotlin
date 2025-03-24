package com.example.havenspure_kotlin_prototype.Map.offline

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Utility to import map tiles from assets or downloaded zip files
 */
class MapFileImporter(private val context: Context) {

    /**
     * Import tiles from assets/tiles directory into app's files directory
     */
    fun importTilesFromAssets() {
        try {
            val targetDir = File(context.filesDir, "tiles")
            if (!targetDir.exists()) targetDir.mkdirs()

            // List all files in the assets/tiles directory
            val assetFiles = context.assets.list("tiles") ?: return

            Log.d("MapFileImporter", "Found ${assetFiles.size} files in assets/tiles")

            // Copy each file to the app's files directory
            for (fileName in assetFiles) {
                try {
                    val inputStream = context.assets.open("tiles/$fileName")
                    val outputFile = File(targetDir, fileName)

                    inputStream.use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    Log.d("MapFileImporter", "Imported tile file: $fileName")
                } catch (e: Exception) {
                    Log.e("MapFileImporter", "Error importing $fileName: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("MapFileImporter", "Error importing tiles from assets: ${e.message}")
        }
    }

    /**
     * Import tiles from a zip file in downloads directory
     */
    fun importTilesFromZip(zipFileName: String) {
        try {
            val targetDir = File(context.filesDir, "tiles")
            if (!targetDir.exists()) targetDir.mkdirs()

            // Try to find the zip file
            val downloadsDir = File(context.getExternalFilesDir(null), "Download")
            val zipFile = File(downloadsDir, zipFileName)

            if (!zipFile.exists()) {
                Log.e("MapFileImporter", "Zip file not found: ${zipFile.absolutePath}")
                return
            }

            // Extract the zip file
            Log.d("MapFileImporter", "Extracting zip file: ${zipFile.absolutePath}")

            val zipInputStream = ZipInputStream(zipFile.inputStream())
            var entry = zipInputStream.nextEntry

            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = File(targetDir, entry.name)

                    // Create parent directories if needed
                    outFile.parentFile?.mkdirs()

                    zipInputStream.use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    Log.d("MapFileImporter", "Extracted file: ${entry.name}")
                }

                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }

            zipInputStream.close()
            Log.d("MapFileImporter", "Zip extraction complete")

        } catch (e: IOException) {
            Log.e("MapFileImporter", "Error extracting zip file: ${e.message}")
        }
    }
}