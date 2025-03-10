// To implement the glowing location icon, you'll need to add a vector drawable resource
// Create a new file in res/drawable/ic_location_blue.xml with this content:

/*
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    
    <!-- Outer glow circle -->
    <path
        android:fillColor="#225D9BFF"
        android:pathData="M12,12m-12,0a12,12 0,1 1,24 0a12,12 0,1 1,-24 0" />
    
    <!-- Middle glow circle -->
    <path
        android:fillColor="#335D9BFF" 
        android:pathData="M12,12m-8,0a8,8 0,1 1,16 0a8,8 0,1 1,-16 0" />
    
    <!-- Inner location dot -->
    <path
        android:fillColor="#5D9BFF"
        android:pathData="M12,12m-4,0a4,4 0,1 1,8 0a4,4 0,1 1,-8 0" />
</vector>
*/

// Alternative approach if you can't add the resource file:
// You can create the location marker icon programmatically:

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/**
 * Creates a glowing location marker icon programmatically.
 *
 * @param context Application context
 * @param color The main color of the marker
 * @return A drawable that can be used as a map marker
 */
fun createGlowingLocationMarker(context: Context, color: Int): Drawable {
    val size = 48 // Size of the marker in pixels
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Create paints
    val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    outerGlowPaint.color = color
    outerGlowPaint.alpha = 40 // Very transparent
    outerGlowPaint.style = Paint.Style.FILL

    val middleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    middleGlowPaint.color = color
    middleGlowPaint.alpha = 80 // Semi-transparent
    middleGlowPaint.style = Paint.Style.FILL

    val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    centerPaint.color = color
    centerPaint.alpha = 255 // Fully opaque
    centerPaint.style = Paint.Style.FILL

    // Draw outer glow
    canvas.drawCircle(size/2f, size/2f, size/2f, outerGlowPaint)

    // Draw middle glow
    canvas.drawCircle(size/2f, size/2f, size/3f, middleGlowPaint)

    // Draw center dot
    canvas.drawCircle(size/2f, size/2f, size/6f, centerPaint)

    return BitmapDrawable(context.resources, bitmap)
}

// Usage:
// In your MapComponent, replace the marker icon code with:
/*
// Create a glowing marker for user location
val markerColor = MaterialTheme.colorScheme.primary.toArgb()
userMarker.icon = createGlowingLocationMarker(context, markerColor)
*/