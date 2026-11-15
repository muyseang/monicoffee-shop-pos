package com.monicoffee.pos.ui.widget

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.monicoffee.pos.data.api.ApiException
import com.monicoffee.pos.data.image.ImageLoader
import com.monicoffee.pos.ui.theme.Palette

/**
 * The building blocks every screen is drawn from: sized and coloured views made in code
 * (the app has no XML layouts).
 */
open class Widgets(val context: Context) {
    fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()

    fun bg(color: Int = Color.WHITE, radius: Int = 12, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); stroke?.let { setStroke(dp(1), it) }
    }

    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    fun text(value: String, sp: Float = 16f, color: Int = Palette.ink, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = sp; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
    }

    fun space(parent: LinearLayout, height: Int) { parent.addView(View(context), LinearLayout.LayoutParams(1, dp(height))) }

    fun divider(parent: LinearLayout) {
        parent.addView(View(context).apply { setBackgroundColor(Palette.line) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(16); bottomMargin = dp(16) })
    }

    fun button(label: String, color: Int = Palette.green, action: () -> Unit): TextView = text(label, 16f, Color.WHITE, true).apply {
        val pad = if (label.length == 1) 0 else 1
        gravity = Gravity.CENTER; background = bg(color); minHeight = dp(56)
        setPadding(dp(16 * pad), dp(12 * pad), dp(16 * pad), dp(12 * pad)); setOnClickListener { action() }
    }

    fun outline(label: String, color: Int, action: () -> Unit): TextView = text(label, 16f, color, true).apply {
        gravity = Gravity.CENTER; background = bg(Color.WHITE, 12, color); minHeight = dp(52); setOnClickListener { action() }
    }

    fun field(hintText: String, value: String = "", numeric: Boolean = false) = EditText(context).apply {
        hint = hintText; setText(value); textSize = 14f; setTextColor(Palette.ink); setHintTextColor(Palette.muted)
        setSingleLine(); background = bg(Color.WHITE, 12, Palette.line); setPadding(dp(16), dp(12), dp(16), dp(12))
        inputType = if (numeric) InputType.TYPE_CLASS_PHONE else InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(16) }
    }

    fun EditText.onChange(action: (String) -> Unit) = addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { action(s.toString()) }
        override fun afterTextChanged(s: Editable?) {}
    })

    fun icon(name: String, color: Int = Palette.green, action: () -> Unit): View = Icon(context, name, color).apply {
        contentDescription = name; setOnClickListener { action() }; layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
    }

    /** A drawing that is only decoration (not a button). */
    fun picture(name: String, color: Int, size: Int): View = Icon(context, name, color).apply {
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { gravity = Gravity.CENTER_HORIZONTAL }
    }

    /** Round − / + button; [solid] is the green one on the details screen, otherwise the outlined one in lists. */
    fun stepper(label: String, size: Int, solid: Boolean, action: () -> Unit): TextView =
        text(label, if (solid) 20f else 16f, if (solid) Color.WHITE else Palette.ink, solid).apply {
            gravity = Gravity.CENTER; includeFontPadding = false
            background = if (solid) bg(Palette.green, 100) else bg(Color.WHITE, 100, Palette.line)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)); setOnClickListener { action() }
        }

    /** Small rounded status badge. */
    fun pill(label: String, color: Int, fill: Int): TextView = text(label, 12f, color, true).apply {
        setPadding(dp(10), dp(3), dp(10), dp(4)); background = bg(fill, 20)
    }

    /** Label on the left, plain value on the right. */
    fun pair(parent: LinearLayout, label: String, value: String, bold: Boolean = true, labelBold: Boolean = false) {
        parent.addView(row().apply {
            addView(text(label, 15f, Palette.ink, labelBold), LinearLayout.LayoutParams(0, -2, 1f))
            addView(text(value, 15f, Palette.ink, bold).apply { gravity = Gravity.END })
        })
    }

    /** Two or three choices sharing one full-width bar (Dine in / Pickup, the My Orders tabs). */
    fun segmented(parent: LinearLayout, values: List<String>, tinted: Boolean, isOn: (String) -> Boolean, action: (String) -> Unit) {
        val bar = row().apply { setPadding(dp(4), dp(4), dp(4), dp(4)); background = if (tinted) bg(Palette.mint, 12) else bg(Color.WHITE, 12, Palette.line) }
        values.forEach { value ->
            val on = isOn(value)
            bar.addView(text(value, 15f, if (on) Color.WHITE else Palette.muted, on).apply {
                gravity = Gravity.CENTER; if (on) background = bg(Palette.green, 10); maxLines = 1
                setOnClickListener { action(value) }
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
        }
        parent.addView(bar)
    }

    /** Centered picture, message and button for a screen that has nothing to list yet. */
    fun empty(parent: LinearLayout, art: String, title: String, detail: String, label: String, action: () -> Unit) {
        space(parent, 96)
        parent.addView(picture(art, Palette.green, 72)); space(parent, 20)
        parent.addView(text(title, 20f, Palette.ink, true).apply { gravity = Gravity.CENTER }); space(parent, 10)
        parent.addView(text(detail, 13f, Palette.muted).apply { gravity = Gravity.CENTER }); space(parent, 28)
        parent.addView(button(label) { action() })
    }

    /** A box with a small label above its content, as the checkout fields are drawn. */
    fun labelled(label: String, content: View, error: Boolean) = column().apply {
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = bg(if (error) Palette.pink else Color.rgb(243, 243, 243), 12, if (error) Palette.red else Palette.green)
        addView(text(label, 12f, if (error) Palette.red else Palette.muted)); addView(content)
    }

    /** Full-screen "couldn't load" state with a retry button (design: Network Error). */
    fun problem(parent: LinearLayout, error: ApiException, retry: () -> Unit) {
        val offline = error.status == 0
        space(parent, 72)
        parent.addView(FrameLayout(context).apply {
            background = bg(Palette.pink, 100)
            addView(Icon(context, "offline", Color.rgb(222, 68, 60)), FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(96), dp(96)).apply { gravity = Gravity.CENTER_HORIZONTAL }); space(parent, 24)
        parent.addView(text(if (offline) "No internet connection" else "Something went wrong", 18f, Palette.ink, true).apply { gravity = Gravity.CENTER }); space(parent, 8)
        parent.addView(text(if (offline) "Please check your connection and try again." else error.message ?: "", 13f, Palette.muted).apply { gravity = Gravity.CENTER })
        // Which server could not be reached — needed when the address typed on the login screen is wrong.
        if (offline) { space(parent, 6); parent.addView(text(error.message ?: "", 11f, Palette.muted).apply { gravity = Gravity.CENTER }) }
        space(parent, 28); parent.addView(button("Try Again") { retry() })
    }

    /** Grey placeholder rows shown while a list is loading (design: Loading). */
    fun skeleton(parent: LinearLayout) {
        val grey = Color.rgb(236, 236, 236)
        val bar = { width: Int, height: Int ->
            View(context).apply { background = bg(grey, 6); layoutParams = LinearLayout.LayoutParams(if (width == 0) -1 else dp(width), dp(height)).apply { bottomMargin = dp(8) } }
        }
        space(parent, 8)
        repeat(4) {
            parent.addView(row().apply {
                gravity = Gravity.TOP
                addView(View(context).apply { background = bg(grey, 10) }, LinearLayout.LayoutParams(dp(64), dp(64)).apply { rightMargin = dp(14) })
                addView(column().apply { addView(bar(120, 14)); addView(bar(0, 12)); addView(bar(70, 12)) }, LinearLayout.LayoutParams(0, -2, 1f))
            }); space(parent, 20)
        }
    }

    /** The design's confirmation dialog: icon, question, a red button that does it and an outlined one that doesn't. */
    fun confirm(art: String, title: String, message: String, yes: String, no: String, action: () -> Unit) {
        val box = column().apply { setPadding(dp(24), dp(28), dp(24), dp(24)); background = bg(Color.WHITE, 20) }
        val dialog = AlertDialog.Builder(context).setView(box).create()
        box.addView(FrameLayout(context).apply {
            background = bg(Palette.pink, 100)
            addView(Icon(context, art, Palette.red), FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(64), dp(64)).apply { gravity = Gravity.CENTER_HORIZONTAL }); space(box, 16)
        box.addView(text(title, 20f, Palette.ink, true).apply { gravity = Gravity.CENTER }); space(box, 8)
        box.addView(text(message, 15f, Color.GRAY).apply { gravity = Gravity.CENTER }); space(box, 20)
        box.addView(button(yes, Palette.red) { dialog.dismiss(); action() }); space(box, 12)
        box.addView(outline(no, Palette.green) { dialog.dismiss() })
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    /** One line of the order-status timeline; [tail] is the colour of the connector to the next step (null for the last). */
    fun step(parent: LinearLayout, mark: String, color: Int, title: String, detail: String, titleColor: Int, detailColor: Int, tail: Int?) {
        parent.addView(row().apply {
            gravity = Gravity.TOP
            addView(column().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(Icon(context, mark, color), LinearLayout.LayoutParams(dp(22), dp(22)))
                if (tail != null) addView(View(context).apply { setBackgroundColor(tail) }, LinearLayout.LayoutParams(dp(2), dp(24)))
            }, LinearLayout.LayoutParams(dp(22), -2).apply { rightMargin = dp(12) })
            addView(column().apply { addView(text(title, 14f, titleColor, true)); addView(text(detail, 12f, detailColor)) }, LinearLayout.LayoutParams(0, -2, 1f))
        })
    }

    fun keyboard(target: View) {
        target.requestFocus()
        target.post { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(target, InputMethodManager.SHOW_IMPLICIT) }
    }

    fun localImage(res: Int, height: Int): ImageView = ImageView(context).apply {
        setImageResource(res); scaleType = ImageView.ScaleType.CENTER_CROP; background = bg(); clipToOutline = true
        layoutParams = LinearLayout.LayoutParams(-1, dp(height))
    }

    /** A photo from the server; shows a plain grey tile until it has downloaded (or if there is none). */
    fun photo(url: String?, height: Int): ImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP; background = bg(Color.rgb(238, 238, 238)); clipToOutline = true
        layoutParams = LinearLayout.LayoutParams(-1, dp(height))
        ImageLoader.load(this, url)
    }

    fun summary(parent: LinearLayout, label: String, value: String, color: Int = Palette.green) {
        parent.addView(row().apply {
            addView(text(label, 16f), LinearLayout.LayoutParams(0, -2, 1f))
            addView(text(value, 16f, color, true).apply { gravity = Gravity.END })
        })
    }

    /** Centered message for empty, loading and error states. */
    fun notice(parent: LinearLayout, title: String, detail: String, retry: (() -> Unit)? = null) {
        space(parent, 60)
        parent.addView(text(title, 20f, Palette.ink, true).apply { gravity = Gravity.CENTER }); space(parent, 10)
        parent.addView(text(detail, 14f, Palette.muted).apply { gravity = Gravity.CENTER })
        if (retry != null) { space(parent, 20); parent.addView(outline("Try again", Palette.green, retry)) }
    }

    fun chips(parent: LinearLayout, values: List<String>, pills: Boolean = false, isOn: (String) -> Boolean, action: (String) -> Unit) {
        val strip = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val r = row()
        values.forEach { value ->
            val on = isOn(value)
            r.addView(text(value, 14f, if (on) Color.WHITE else if (pills) Palette.green else Palette.muted, on).apply {
                gravity = Gravity.CENTER; setPadding(dp(14), dp(9), dp(14), dp(9))
                background = if (pills) bg(if (on) Palette.green else Color.WHITE, 20, Palette.green) else bg(if (on) Palette.green else Palette.soft, 8)
                setOnClickListener { action(value) }
            }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) })
        }
        strip.addView(r); parent.addView(strip)
    }

    fun centered(parent: LinearLayout, view: View) { parent.addView(row().apply { gravity = Gravity.CENTER; addView(view) }) }
}
