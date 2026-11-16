package com.monicoffee.pos.ui.widget

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
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
import android.widget.ProgressBar
import android.widget.TextView
import com.monicoffee.pos.R
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

    /** The welcome and sign-in designs set "bold" text in PingFang SC Semibold, which Android's medium weight matches. */
    fun TextView.semibold() = apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }

    /** Sets the line height to [multiple] × the text size, like the design's line-height (Android's own multiplier starts from the taller font height). */
    fun TextView.leading(multiple: Float) = apply { setLineSpacing(textSize * multiple - paint.getFontMetricsInt(null), 1f) }

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

    /**
     * Designs 06/08/33: a 52dp field with a 1.5dp border (green while typing, or always when [green]);
     * its placeholder moves onto the border as a 10sp label once it has text.
     */
    fun outlinedField(label: String, value: String, numeric: Boolean = false, green: Boolean = false): Pair<FrameLayout, EditText> {
        val input = field(label, value, numeric).apply {
            setPadding(dp(24), 0, dp(24), 0); letterSpacing = -0.01f
            layoutParams = FrameLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(5) }
        }
        val tag = text(label, 10f, Palette.ink).apply { setBackgroundColor(Color.WHITE); setPadding(dp(8), 0, dp(8), 0); includeFontPadding = false }
        val box = FrameLayout(context).apply {
            addView(input); addView(tag, FrameLayout.LayoutParams(-2, dp(10)).apply { leftMargin = dp(19) })
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = -dp(5) }
        }
        val paint = {
            tag.visibility = if (input.text.isEmpty()) View.INVISIBLE else View.VISIBLE
            input.background = bg(Color.WHITE, 12).apply { setStroke(dp(3) / 2, if (green || input.hasFocus()) Palette.green else Palette.line) }
        }
        paint(); input.onChange { paint() }; input.setOnFocusChangeListener { _, _ -> paint() }
        return box to input
    }

    fun EditText.onChange(action: (String) -> Unit) = addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { action(s.toString()) }
        override fun afterTextChanged(s: Editable?) {}
    })

    fun icon(name: String, color: Int = Palette.green, action: () -> Unit): View = Icon(context, name, color).apply {
        contentDescription = name; setOnClickListener { action() }; layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
    }

    /** One of the design's icons (res/drawable/ic_*), drawn at its own size and optionally recoloured. */
    fun vector(res: Int, tint: Int? = null): ImageView = ImageView(context).apply {
        setImageResource(res); scaleType = ImageView.ScaleType.CENTER; tint?.let { imageTintList = ColorStateList.valueOf(it) }
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
        // Untinted: the My Orders tab bar (design 45), 8dp inside a 1.5dp #F4F4F4 border, 14sp tabs with 9dp corners.
        val bar = row().apply {
            if (tinted) { setPadding(dp(4), dp(4), dp(4), dp(4)); background = bg(Palette.mint, 12) }
            else { setPadding(dp(8), dp(8), dp(8), dp(8)); background = bg(Color.WHITE, 12).apply { setStroke(dp(3) / 2, Color.rgb(244, 244, 244)) } }
        }
        values.forEach { value ->
            val on = isOn(value)
            bar.addView(text(value, if (tinted) 15f else 14f, if (on) Color.WHITE else Palette.muted, on).apply {
                gravity = Gravity.CENTER; if (on) background = bg(Palette.green, if (tinted) 10 else 9); maxLines = 1; letterSpacing = -0.01f
                setOnClickListener { action(value) }
            }, LinearLayout.LayoutParams(0, dp(if (tinted) 42 else 41), 1f))
        }
        parent.addView(bar)
    }

    /** Empty state (design 38): a 72dp icon, an 18sp title, a 12sp line and the button, low in the page. */
    fun empty(parent: LinearLayout, art: View, title: String, detail: String, label: String, top: Int = 175, action: () -> Unit) {
        space(parent, top)
        parent.addView(art, LinearLayout.LayoutParams(dp(72), dp(72)).apply { gravity = Gravity.CENTER_HORIZONTAL }); space(parent, 60)
        parent.addView(text(title, 18f, Palette.ink, true).apply { gravity = Gravity.CENTER; letterSpacing = -0.02f }); space(parent, 12)
        parent.addView(text(detail, 12f, Palette.muted).apply { gravity = Gravity.CENTER; letterSpacing = -0.01f; leading(1.6f) },
            LinearLayout.LayoutParams(dp(265), -2).apply { gravity = Gravity.CENTER_HORIZONTAL }); space(parent, 40)
        parent.addView(button(label) { action() }, LinearLayout.LayoutParams(-1, dp(56)))
    }

    /** A box with a small label above its content, as the checkout fields are drawn. */
    fun labelled(label: String, content: View, error: Boolean) = column().apply {
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = bg(if (error) Palette.pink else Color.rgb(243, 243, 243), 12).apply { setStroke(dp(3) / 2, if (error) Palette.red else Palette.green) }
        addView(text(label, 12f, if (error) Palette.red else Palette.muted)); addView(content)
    }

    /** Full-screen "couldn't load" state with a retry button (design: Network Error). */
    fun problem(parent: LinearLayout, error: ApiException, retry: () -> Unit) {
        val offline = error.status == 0
        // Design 42: a 120dp pink circle with the crossed-out wifi, the 18sp title and 14sp line 12dp apart.
        space(parent, 72)
        parent.addView(FrameLayout(context).apply {
            background = bg(Color.rgb(253, 241, 241), 60)
            addView(vector(R.drawable.ic_wifi_off), FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(120), dp(120)).apply { gravity = Gravity.CENTER_HORIZONTAL }); space(parent, 32)
        parent.addView(text(if (offline) "No internet connection" else "Something went wrong", 18f, Palette.ink, true).apply { gravity = Gravity.CENTER }); space(parent, 12)
        parent.addView(text(if (offline) "Please check your connection and try again." else error.message ?: "", 14f, Palette.muted).apply { gravity = Gravity.CENTER })
        // Which server could not be reached — needed when the address typed on the login screen is wrong.
        if (offline) { space(parent, 6); parent.addView(text(error.message ?: "", 11f, Palette.muted).apply { gravity = Gravity.CENTER }) }
        space(parent, 40); parent.addView(button("Try Again") { retry() }, LinearLayout.LayoutParams(-1, dp(56)))
    }

    /** Grey placeholder rows shown while a list is loading (design: Loading). */
    fun skeleton(parent: LinearLayout, label: String = "Loading…") {
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
        // Design 43: a spinner and "Loading menu…" under the placeholders.
        space(parent, 24)
        parent.addView(ProgressBar(context).apply { indeterminateTintList = ColorStateList.valueOf(Palette.muted) }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        space(parent, 8); parent.addView(text(label, 12f, Palette.muted).apply { gravity = Gravity.CENTER })
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
        // Design 27/46 timeline: a 24dp dot, a 2×28dp connector, the text 14dp to the right.
        parent.addView(row().apply {
            gravity = Gravity.TOP; setPadding(dp(4), 0, 0, 0)
            addView(column().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(Icon(context, mark, color), LinearLayout.LayoutParams(dp(24), dp(24)))
                if (tail != null) addView(View(context).apply { setBackgroundColor(tail) }, LinearLayout.LayoutParams(dp(2), dp(28)))
            }, LinearLayout.LayoutParams(dp(24), -2).apply { rightMargin = dp(14) })
            addView(column().apply {
                setPadding(0, dp(2), 0, 0)
                addView(text(title, 14f, titleColor, true)); space(this, 2); addView(text(detail, 12f, detailColor))
            }, LinearLayout.LayoutParams(0, -2, 1f))
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
            val chip = text(value, 14f, if (on) Color.WHITE else Palette.muted, on).apply {
                gravity = Gravity.CENTER; setPadding(dp(14), dp(9), dp(14), dp(9))
                background = bg(if (on) Palette.green else Palette.soft, 8)
                setOnClickListener { action(value) }
            }
            // The Details options (design 39): 12sp, 12×5 padding, 12dp corners; chosen ones filled green, the rest outlined.
            if (pills) chip.apply {
                textSize = 12f; setTypeface(null, Typeface.NORMAL); setTextColor(if (on) Color.WHITE else Color.rgb(119, 119, 119))
                setPadding(dp(12), dp(5), dp(12), dp(5)); minHeight = dp(32)
                background = if (on) bg(Palette.green, 12) else bg(Color.TRANSPARENT, 12, Palette.green)
            }
            // Home's category chips (Mainpage design): 10×6 padding, a cup on the chosen one, see-through grey otherwise.
            if (!pills) chip.apply {
                setPadding(dp(10), dp(6), dp(10), dp(6)); leading(1.5f)
                if (on) { setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_cup, 0, 0, 0); compoundDrawablePadding = dp(10) }
                else background = bg(Color.argb(89, 237, 237, 237), 8)
            }
            r.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(if (pills) 15 else 12) })
        }
        strip.addView(r); parent.addView(strip)
    }

    fun centered(parent: LinearLayout, view: View) { parent.addView(row().apply { gravity = Gravity.CENTER; addView(view) }) }
}
