package com.monicoffee.pos.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Each customer's profile photo, kept on the phone as a JPEG in the app's private files.
 * The backend has no profile-photo field, so the photo isn't sent anywhere; it is stored per
 * account id, so two people signing in on the same phone each keep their own.
 */
class ProfilePhotos(private val context: Context) {
    private var cachedId = -1
    private var cached: Bitmap? = null

    /** The saved photo of account [userId], or null when it has none. */
    fun get(userId: Int?): Bitmap? {
        if (userId == null || userId == 0) return null
        if (userId != cachedId) {
            cached = file(userId).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
            cachedId = userId
        }
        return cached
    }

    /** Stores [jpeg] (already shrunk) as the photo of account [userId]. */
    fun save(userId: Int, jpeg: ByteArray, bitmap: Bitmap) {
        file(userId).writeBytes(jpeg)
        cachedId = userId; cached = bitmap
    }

    fun remove(userId: Int) {
        file(userId).delete()
        if (cachedId == userId) cached = null
    }

    private fun file(userId: Int) = File(context.filesDir, "profile_photo_$userId.jpg")
}
