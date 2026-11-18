package com.monicoffee.pos.ui.profile

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.data.api.ApiException
import com.monicoffee.pos.data.image.ImageLoader
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.theme.Palette
import com.monicoffee.pos.ui.widget.PhotoCropView
import java.io.ByteArrayOutputStream

/** Design 33: the avatar and the outlined name, address and phone fields, then Save. */
class PersonalInfoScreen(app: MainActivity) : Screen(app) {
    /** A new picture (bitmap and its JPEG) waiting for Save. */
    private var pending: Pair<Bitmap, ByteArray>? = null
    /** The customer chose Remove photo; applied on Save. */
    private var removing = false
    private var avatarBox: FrameLayout? = null

    override fun draw() {
        // Opening the screen starts from the saved photo; an unsaved change is dropped.
        if (!quiet) { pending = null; removing = false }
        header("Personal Info"); val b = content(); space(b, 24)
        val box = FrameLayout(context).apply { setOnClickListener { photoOptions() }; contentDescription = "Change profile photo" }
        avatarBox = box; fillAvatar()
        b.addView(box, LinearLayout.LayoutParams(dp(120), dp(120)).apply { gravity = Gravity.CENTER_HORIZONTAL }); space(b, 8)
        b.addView(text("Change photo", 13f, Palette.green, true).apply { gravity = Gravity.CENTER; setOnClickListener { photoOptions() } }, LinearLayout.LayoutParams(-1, -2))
        space(b, 52)
        val name = outlinedField("Full name", state.profile?.fullName ?: "", green = true); b.addView(name.first); space(b, 24)
        val address = outlinedField("Address", state.profile?.address ?: "", green = true); b.addView(address.first); space(b, 24)
        // The phone number is the login, so the server doesn't let it change here.
        val phone = outlinedField("Phone number", state.profile?.phone ?: "", numeric = true, green = true); b.addView(phone.first)
        phone.second.apply { isEnabled = false; setTextColor(Palette.ink) }
        footer("Save") {
            if (name.second.text.isBlank()) name.second.error = "Enter your name"
            else submit({ api.updateMe(name.second.text.toString().trim(), address.second.text.toString().trim()) }) {
                session.save(it); savePhoto(it.id); toast("Saved"); back()
            }
        }
    }

    /** The photo shown: the one waiting for Save, else the saved one, else the initials. */
    private fun shownPhoto(): Bitmap? = when {
        removing -> null
        else -> pending?.first ?: app.photos.get(state.profile?.id)
    }

    private fun fillAvatar() {
        val box = avatarBox ?: return
        box.removeAllViews()
        box.addView(avatar(shownPhoto(), state.initials, 120, 40f), FrameLayout.LayoutParams(-1, -1))
        // A small green "+" badge says the picture can be changed.
        box.addView(text("+", 20f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; includeFontPadding = false
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Palette.green); setStroke(dp(3), Color.WHITE) }
        }, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.BOTTOM or Gravity.END))
    }

    private fun photoOptions() {
        val choices = mutableListOf("Choose from gallery")
        if (shownPhoto() != null) choices.add("Remove photo")
        AlertDialog.Builder(context).setTitle("Profile photo").setItems(choices.toTypedArray()) { _, i ->
            if (choices[i] == "Remove photo") { pending = null; removing = true; fillAvatar() } else pickPhoto()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun pickPhoto() {
        try {
            @Suppress("DEPRECATION")
            app.startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), MainActivity.PICK_PHOTO)
        } catch (e: Exception) { toast("No app available to choose a picture.") }
    }

    /** Called with the picture the customer chose: opens the editor to fit it to the round frame. */
    fun onPicked(picture: Uri) {
        load({
            val bytes = app.contentResolver.openInputStream(picture)?.use { it.readBytes() }
            val full = bytes?.let { ImageLoader.decode(it, EDIT_SIZE) } ?: throw ApiException(0, "That file couldn't be read as a picture.")
            ImageLoader.upright(full, bytes)
        }, sticky = true, failed = { alert(it.message) }) { full -> edit(full) }
    }

    /**
     * Full-screen editor: drag and pinch the photo until the right part fills the circle, turn it
     * if it is sideways, then Use Photo. Shown as a dialog over this screen, so what was typed in
     * the fields stays.
     */
    private fun edit(full: Bitmap) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar)
        val crop = PhotoCropView(context, full)
        val page = column().apply { setBackgroundColor(Color.BLACK) }
        page.addView(text("Move and Scale", 18f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(24), 0, dp(4)) }, LinearLayout.LayoutParams(-1, -2))
        page.addView(text("Drag to move, pinch to zoom", 13f, Color.rgb(170, 170, 170)).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(16)) }, LinearLayout.LayoutParams(-1, -2))
        page.addView(crop, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(text("↻  Rotate", 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(20), dp(10), dp(20), dp(10)); background = bg(Color.rgb(45, 45, 45), 100)
            contentDescription = "Rotate photo"; setOnClickListener { crop.rotate() }
        }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(20) })
        val buttons = row().apply { setPadding(dp(24), 0, dp(24), dp(28)) }
        buttons.addView(outline("Cancel", Color.WHITE) { dialog.dismiss() }.apply { background = bg(Color.BLACK, 12, Color.WHITE) },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(12) })
        buttons.addView(button("Use Photo") {
            val square = crop.crop(PHOTO_SIZE)
            dialog.dismiss()
            load({ square to ByteArrayOutputStream().also { square.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray() },
                sticky = true, failed = { alert(it.message) }) { photo ->
                pending = photo; removing = false
                // Update only the picture: redrawing would reset what was typed in the fields.
                fillAvatar()
            }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        page.addView(buttons)
        dialog.setContentView(page)
        dialog.window?.apply {
            setLayout(-1, -1)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.BLACK; navigationBarColor = Color.BLACK
            @Suppress("DEPRECATION") decorView.systemUiVisibility = 0 // light icons on the black bars
        }
        dialog.show()
    }

    private fun savePhoto(userId: Int) {
        val photo = pending
        if (photo != null) app.photos.save(userId, photo.second, photo.first)
        else if (removing) app.photos.remove(userId)
        pending = null; removing = false
    }

    private companion object {
        /** Size of the kept photo; the avatar is at most 120dp across. */
        const val PHOTO_SIZE = 512
        /** Longest side the editor works with: sharp after zooming in, without using much memory. */
        const val EDIT_SIZE = 1600
    }
}
