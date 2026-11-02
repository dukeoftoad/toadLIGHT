package com.toadlight.filter.filter

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WallpaperSnapshot(val home: ImageBitmap?, val lock: ImageBitmap?)

/**
 * Reads the user's current home and lock-screen wallpapers so the app can
 * preview them under the live filter (tint + paper + dim).
 *
 * Read-only, off the main thread, no permissions: both APIs are readable by
 * any app. Live wallpapers have no static image — callers get null and show
 * a placeholder instead.
 */
object WallpaperSource {

    private const val MAX_WIDTH = 720

    suspend fun load(context: Context): WallpaperSnapshot = withContext(Dispatchers.IO) {
        runCatching {
            val wm = WallpaperManager.getInstance(context)
            val home = wm.drawable.toPreviewImage()
            val lock = wm.getWallpaperFile(WallpaperManager.FLAG_LOCK)
                ?.let { pfd ->
                    runCatching {
                        BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
                    }.getOrNull()
                }
                ?.let { decoded -> decoded.scaleDown().asImageBitmap() }
            WallpaperSnapshot(home, lock)
        }.getOrDefault(WallpaperSnapshot(null, null))
    }

    private fun Bitmap.scaleDown(maxWidth: Int = MAX_WIDTH): Bitmap {
        if (width <= maxWidth) return this
        val scale = maxWidth.toFloat() / width
        return Bitmap.createScaledBitmap(
            this,
            maxWidth,
            (height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun Drawable?.toPreviewImage(): ImageBitmap? {
        if (this == null) return null
        val src = when (this) {
            is BitmapDrawable -> bitmap
            else -> {
                val w = intrinsicWidth
                val h = intrinsicHeight
                if (w <= 0 || h <= 0) return null
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                    val canvas = Canvas(bmp)
                    setBounds(0, 0, w, h)
                    draw(canvas)
                }
            }
        }
        val scale = (MAX_WIDTH.toFloat() / src.width).coerceAtMost(1f)
        val out = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                src,
                (src.width * scale).toInt().coerceAtLeast(1),
                (src.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            src
        }
        return out.asImageBitmap()
    }
}
