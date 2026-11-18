package com.monicoffee.pos.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import com.monicoffee.pos.data.api.connect
import java.util.concurrent.Executors

/** Loads menu photos and QR codes from the server into ImageViews, with a memory cache. */
object ImageLoader {
    private const val MAX_SIDE = 720
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun load(view: ImageView, url: String?) {
        view.tag = url
        view.setImageDrawable(null)
        if (url == null) return
        cache.get(url)?.let { view.setImageBitmap(it); return }
        pool.execute {
            val bitmap = try { fetch(url) } catch (e: Exception) { null } ?: return@execute
            cache.put(url, bitmap)
            // The view may have been reused for another picture while this one downloaded.
            main.post { if (view.tag == url) view.setImageBitmap(bitmap) }
        }
    }

    private fun fetch(url: String): Bitmap? {
        val connection = connect(url)
        connection.connectTimeout = 8000
        connection.readTimeout = 20000
        try {
            val bytes = connection.inputStream.use { it.readBytes() }
            return decode(bytes, MAX_SIDE)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Turns a phone camera photo the right way up. Cameras often save the picture sideways and
     * only note the turn in the file's EXIF data, which decoding ignores.
     */
    fun upright(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val degrees = try {
            when (ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: Exception) { 0f } // not a JPEG with EXIF data, e.g. a PNG
        if (degrees == 0f) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
    }

    /** Decodes at reduced resolution so a large photo never needs more than ~maxSide pixels a side. */
    fun decode(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
