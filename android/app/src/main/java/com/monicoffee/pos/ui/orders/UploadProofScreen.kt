package com.monicoffee.pos.ui.orders

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.data.api.ApiException
import com.monicoffee.pos.data.image.ImageLoader
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette
import java.io.ByteArrayOutputStream

/** Upload Screenshot: pick the transfer screenshot, check it, then send it for staff to verify. */
class UploadProofScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        val o = orders.current ?: return show(Route.ORDERS)
        header("Upload Screenshot"); val b = content()
        amount(b, "Amount Paid", o)
        val preview = orders.proofPreview
        val box = column().apply {
            gravity = Gravity.CENTER; setPadding(dp(16), dp(28), dp(16), dp(28))
            background = GradientDrawable().apply {
                setColor(Color.rgb(243, 247, 245)); cornerRadius = dp(12).toFloat(); setStroke(dp(2), Palette.green, dp(6).toFloat(), dp(4).toFloat())
            }
            if (preview == null) {
                addView(picture("upload", Palette.green, 40)); space(this, 10)
                addView(text("Tap to upload screenshot", 15f, Palette.ink, true).apply { gravity = Gravity.CENTER }); space(this, 4)
                addView(text("JPG or PNG", 12f, Palette.muted).apply { gravity = Gravity.CENTER })
            } else {
                addView(ImageView(context).apply { setImageBitmap(preview); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(-1, dp(220))); space(this, 10)
                addView(text("Tap to choose another", 12f, Palette.green, true).apply { gravity = Gravity.CENTER })
            }
            setOnClickListener {
                try {
                    app.startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), MainActivity.PICK_PROOF)
                } catch (e: Exception) { toast("No app available to choose a picture.") }
            }
        }
        b.addView(FrameLayout(context).apply {
            background = bg(Color.WHITE, 16); elevation = dp(3).toFloat(); setPadding(dp(10), dp(10), dp(10), dp(10)); addView(box)
        }, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(2); rightMargin = dp(2); topMargin = dp(4); bottomMargin = dp(4) })
        space(b, 28)
        centered(b, if (preview == null) pill("●  Screenshot required before confirmation", Palette.amber, Palette.cream) else pill("●  Screenshot ready to submit", Palette.green, Color.rgb(219, 242, 230)))
        space(b, 12)
        b.addView(text("Make sure the amount, date and time, and reference number are clearly visible in your screenshot.", 13f, Palette.muted).apply { gravity = Gravity.CENTER; setLineSpacing(dp(3).toFloat(), 1f) })
        footer("Submit for Verification", if (orders.proofBytes == null) Color.rgb(200, 200, 200) else Palette.green) {
            val jpeg = orders.proofBytes ?: return@footer toast("Choose your payment screenshot first.")
            submit({ api.uploadPaymentProof(o.id, jpeg) }) {
                orders.current = it; orders.byTab.clear(); orders.proofBytes = null; orders.proofPreview = null
                toast("Screenshot sent. Staff will confirm your payment shortly.")
                history.clear(); history.addLast(Route.ORDERS); show(Route.TRACK_ORDER)
            }
        }
    }

    /** Called with the picture the customer chose in the system picker. */
    fun onPicked(picture: Uri) {
        load({
            // Shrink to a JPEG well under the server's 2 MB limit; it is sent when the customer taps Submit.
            val bytes = app.contentResolver.openInputStream(picture)?.use { it.readBytes() }
            val bitmap = bytes?.let { ImageLoader.decode(it, 1600) } ?: throw ApiException(0, "That file couldn't be read as a picture.")
            bitmap to ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        }, sticky = true, failed = { alert(it.message) }) { (bitmap, jpeg) ->
            orders.proofPreview = bitmap; orders.proofBytes = jpeg
            if (screen == Route.UPLOAD_PROOF) redraw()
        }
    }
}
