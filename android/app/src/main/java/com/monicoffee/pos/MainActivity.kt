package com.monicoffee.pos

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The Moni Coffee customer app: one activity that draws each screen in code and
 * talks to the Django backend through [ApiClient]. Screens are re-drawn from the
 * fields below whenever something changes.
 */
class MainActivity : Activity() {
    private val green = Color.rgb(0, 125, 87)
    private val ink = Color.rgb(44, 49, 48)
    private val muted = Color.rgb(160, 160, 160)
    private val line = Color.rgb(232, 232, 232)
    private val red = Color.rgb(190, 46, 31)
    private val soft = Color.rgb(247, 247, 247)
    private val mint = Color.rgb(226, 240, 234)
    private val pink = Color.rgb(255, 238, 236)
    private val dark = Color.rgb(31, 31, 31)

    private lateinit var prefs: SharedPreferences
    private lateinit var api: ApiClient
    private val io = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())

    // --- screen plumbing ---
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private var scroll: ScrollView? = null
    private var screen = ""
    private val history = java.util.ArrayDeque<String>()
    /** Goes up on every redraw, so a reply meant for a screen that is gone can be dropped. */
    private var generation = 0
    /** True while a screen is only being redrawn with data it just loaded (don't load again). */
    private var quiet = false
    private var poller: Runnable? = null
    private var wait: AlertDialog? = null

    // --- session ---
    private var profile: Profile? = null
    private val signedIn get() = api.token != null
    private var authPurpose = "login"
    private var authPhone = ""
    private var authName = ""
    private var ticket: OtpTicket? = null
    /** When [ticket] was issued, to count down its expiry. */
    private var ticketAt = 0L
    private var authCode = ""
    private var splashShown = false

    // --- menu ---
    private var categories = listOf<Category>()
    private var items = listOf<MenuItem>()
    private var menuLoadedAt = 0L
    private var menuLoading = false
    private var menuError: ApiException? = null
    private var categoryId = 0 // 0 = all categories
    private var query = ""
    private val favorites = mutableSetOf<Int>()

    // --- item details ---
    private var selected: MenuItem? = null
    private var quantity = 1
    /** Option id -> chosen value ("" for an add-on that has no choices). */
    private val picks = mutableMapOf<Int, String>()

    // --- cart and checkout ---
    private data class CartItem(val item: MenuItem, val picks: Map<Int, String>, var count: Int) {
        val unitPrice get() = item.price + item.options.filter { it.id in picks }.sumOf { it.extraPrice }
        val label
            get() = item.options.filter { it.id in picks }
                .joinToString(" · ") { if (it.choices.isEmpty()) it.name else "${it.name}: ${picks[it.id]}" }
    }

    private val cart = mutableListOf<CartItem>()
    private var pickup = false
    private var tableNumber = ""
    private var pickupTime: String? = null
    private var checkoutError = false
    private var payment = "cash"
    private var qr: QrPayment? = null

    // --- orders ---
    private val orderTabs = listOf("In Progress" to "active", "Completed" to "completed", "Cancelled" to "cancelled")
    private var orderTab = "active"
    private val ordersByTab = mutableMapOf<String, List<Order>>()
    private var ordersError: ApiException? = null
    /** The payment screenshot the customer picked, shrunk to a JPEG, and its preview. */
    private var proofBytes: ByteArray? = null
    private var proofPreview: Bitmap? = null
    private var slide = 0
    private var currentOrder: Order? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("moni", MODE_PRIVATE)
        api = ApiClient(prefs.getString("server", DEFAULT_SERVER)!!, prefs.getString("token", null))
        if (signedIn) {
            profile = Profile(
                prefs.getInt("id", 0),
                prefs.getString("name", "")!!,
                prefs.getString("phone", "")!!,
                prefs.getString("address", "")!!,
            )
        }
        favorites.addAll(prefs.getStringSet("favorites", emptySet())!!.mapNotNull { it.toIntOrNull() })
        payment = prefs.getString("payment", "cash")!!
        show("Splash")
        main.postDelayed({
            if (!isDestroyed && screen == "Splash") show(if (signedIn) "Home" else if (prefs.getBoolean("onboarded", false)) "Login" else "Onboarding")
        }, SPLASH_MS)
    }

    override fun onStop() {
        super.onStop()
        poller?.let(main::removeCallbacks)
    }

    override fun onRestart() {
        super.onRestart()
        // Restart polling and refresh what's on screen — except forms, which would lose what was typed.
        if (screen !in setOf("Login", "Register", "Verification", "Verifying", "Personal Info", "Splash")) show(screen)
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdown()
    }

    // ------------------------------------------------------------------ background work

    /** Runs [work] off the main thread for the screen being shown; the reply is dropped if the screen changed. */
    private fun <T> load(
        work: () -> T,
        sticky: Boolean = false,
        failed: (ApiException) -> Unit = { toast(it.message) },
        done: (T) -> Unit,
    ) {
        val asked = generation
        io.execute {
            try {
                val result = work()
                main.post { if (!isDestroyed && (sticky || asked == generation)) done(result) }
            } catch (e: Exception) {
                val error = e as? ApiException ?: ApiException(0, "The server sent an unexpected response.")
                main.post {
                    if (isDestroyed) return@post
                    if (error.status == 401 && signedIn) expire()
                    else if (sticky || asked == generation) failed(error)
                }
            }
        }
    }

    /** Runs an action the customer asked for (place order, save…) behind a "please wait" dialog. */
    private fun <T> submit(work: () -> T, done: (T) -> Unit) {
        val dialog = AlertDialog.Builder(this).setMessage("Please wait…").setCancelable(false).show()
        wait = dialog
        load(work, sticky = true, failed = { dialog.dismiss(); alert(it.message) }) { dialog.dismiss(); done(it) }
    }

    private fun every(millis: Long, tick: () -> Unit) {
        val task = object : Runnable {
            override fun run() { tick(); main.postDelayed(this, millis) }
        }
        poller = task
        main.postDelayed(task, millis)
    }

    private fun toast(message: String?) = Toast.makeText(this, message ?: "Something went wrong", Toast.LENGTH_LONG).show()

    private fun alert(message: String?) {
        if (!isDestroyed) AlertDialog.Builder(this).setMessage(message ?: "Something went wrong").setPositiveButton("OK", null).show()
    }

    // ------------------------------------------------------------------ session

    private fun signIn(session: Session) {
        api.token = session.token
        prefs.edit().putString("token", session.token).apply()
        saveProfile(session.profile)
        history.clear()
        menuLoadedAt = 0
        if (authPurpose == "register") show("Registered")
        else { show("Home"); toast(if (session.profile.fullName.isBlank()) "Welcome!" else "Welcome, ${session.profile.fullName}!") }
    }

    private fun saveProfile(value: Profile) {
        profile = value
        prefs.edit().putInt("id", value.id).putString("name", value.fullName)
            .putString("phone", value.phone).putString("address", value.address).apply()
    }

    private fun signOut() {
        wait?.dismiss()
        api.token = null
        profile = null
        prefs.edit().remove("token").remove("id").remove("name").remove("phone").remove("address").apply()
        cart.clear(); ordersByTab.clear(); currentOrder = null; history.clear()
        show("Login")
    }

    /** The server no longer accepts our token (e.g. logged out from another phone). */
    private fun expire() {
        signOut()
        toast("Please log in again.")
    }

    // ------------------------------------------------------------------ view helpers

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun money(n: Double) = String.format(Locale.US, "$%.2f", n)
    private fun count(n: Int) = if (n == 1) "1 item" else "$n items"
    private fun bg(color: Int = Color.WHITE, radius: Int = 12, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); stroke?.let { setStroke(dp(1), it) }
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun text(value: String, sp: Float = 16f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = sp; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun space(parent: LinearLayout, height: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun divider(parent: LinearLayout) {
        parent.addView(View(this).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(16); bottomMargin = dp(16) })
    }
    private fun button(label: String, color: Int = green, action: () -> Unit): TextView = text(label, 16f, Color.WHITE, true).apply {
        val pad = if (label.length == 1) 0 else 1
        gravity = Gravity.CENTER; background = bg(color); minHeight = dp(56)
        setPadding(dp(16 * pad), dp(12 * pad), dp(16 * pad), dp(12 * pad)); setOnClickListener { action() }
    }
    private fun outline(label: String, color: Int, action: () -> Unit): TextView = text(label, 16f, color, true).apply {
        gravity = Gravity.CENTER; background = bg(Color.WHITE, 12, color); minHeight = dp(52); setOnClickListener { action() }
    }
    private fun field(hintText: String, value: String = "", numeric: Boolean = false) = EditText(this).apply {
        hint = hintText; setText(value); textSize = 14f; setTextColor(ink); setHintTextColor(muted)
        setSingleLine(); background = bg(Color.WHITE, 12, line); setPadding(dp(16), dp(12), dp(16), dp(12))
        inputType = if (numeric) InputType.TYPE_CLASS_PHONE else InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(16) }
    }
    private fun EditText.onChange(action: (String) -> Unit) = addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { action(s.toString()) }
        override fun afterTextChanged(s: Editable?) {}
    })
    private fun icon(name: String, color: Int = green, action: () -> Unit): View = Icon(this, name, color).apply {
        contentDescription = name; setOnClickListener { action() }; layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
    }
    /** A drawing that is only decoration (not a button). */
    private fun picture(name: String, color: Int, size: Int): View = Icon(this, name, color).apply {
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { gravity = Gravity.CENTER_HORIZONTAL }
    }
    /** Round − / + button; [solid] is the green one on the details screen, otherwise the outlined one in lists. */
    private fun stepper(label: String, size: Int, solid: Boolean, action: () -> Unit): TextView =
        text(label, if (solid) 20f else 16f, if (solid) Color.WHITE else ink, solid).apply {
            gravity = Gravity.CENTER; includeFontPadding = false
            background = if (solid) bg(green, 100) else bg(Color.WHITE, 100, line)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)); setOnClickListener { action() }
        }
    /** Small rounded status badge. */
    private fun pill(label: String, color: Int, fill: Int): TextView = text(label, 12f, color, true).apply {
        setPadding(dp(10), dp(3), dp(10), dp(4)); background = bg(fill, 20)
    }
    /** Label on the left, plain value on the right. */
    private fun pair(parent: LinearLayout, label: String, value: String, bold: Boolean = true, labelBold: Boolean = false) {
        parent.addView(row().apply {
            addView(text(label, 15f, ink, labelBold), LinearLayout.LayoutParams(0, -2, 1f))
            addView(text(value, 15f, ink, bold).apply { gravity = Gravity.END })
        })
    }
    /** Two or three choices sharing one full-width bar (Dine in / Pickup, the My Orders tabs). */
    private fun segmented(parent: LinearLayout, values: List<String>, tinted: Boolean, isOn: (String) -> Boolean, action: (String) -> Unit) {
        val bar = row().apply { setPadding(dp(4), dp(4), dp(4), dp(4)); background = if (tinted) bg(mint, 12) else bg(Color.WHITE, 12, line) }
        values.forEach { value ->
            val on = isOn(value)
            bar.addView(text(value, 15f, if (on) Color.WHITE else muted, on).apply {
                gravity = Gravity.CENTER; if (on) background = bg(green, 10); maxLines = 1
                setOnClickListener { action(value) }
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
        }
        parent.addView(bar)
    }
    /** Centered picture, message and button for a screen that has nothing to list yet. */
    private fun empty(parent: LinearLayout, art: String, title: String, detail: String, label: String, action: () -> Unit) {
        space(parent, 96)
        parent.addView(picture(art, green, 72)); space(parent, 20)
        parent.addView(text(title, 20f, ink, true).apply { gravity = Gravity.CENTER }); space(parent, 10)
        parent.addView(text(detail, 13f, muted).apply { gravity = Gravity.CENTER }); space(parent, 28)
        parent.addView(button(label) { action() })
    }
    /** A box with a small label above its content, as the checkout fields are drawn. */
    private fun labelled(label: String, content: View, error: Boolean) = column().apply {
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = bg(if (error) pink else Color.rgb(243, 243, 243), 12, if (error) red else green)
        addView(text(label, 12f, if (error) red else muted)); addView(content)
    }
    /** Full-screen "couldn't load" state with a retry button (design: Network Error). */
    private fun problem(parent: LinearLayout, error: ApiException, retry: () -> Unit) {
        val offline = error.status == 0
        space(parent, 72)
        parent.addView(FrameLayout(this).apply {
            background = bg(pink, 100)
            addView(Icon(this@MainActivity, "offline", Color.rgb(222, 68, 60)), FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(96), dp(96)).apply { gravity = Gravity.CENTER_HORIZONTAL }); space(parent, 24)
        parent.addView(text(if (offline) "No internet connection" else "Something went wrong", 18f, ink, true).apply { gravity = Gravity.CENTER }); space(parent, 8)
        parent.addView(text(if (offline) "Please check your connection and try again." else error.message ?: "", 13f, muted).apply { gravity = Gravity.CENTER })
        // Which server could not be reached — needed when the address typed on the login screen is wrong.
        if (offline) { space(parent, 6); parent.addView(text(error.message ?: "", 11f, muted).apply { gravity = Gravity.CENTER }) }
        space(parent, 28); parent.addView(button("Try Again") { retry() })
    }
    /** Grey placeholder rows shown while a list is loading (design: Loading). */
    private fun skeleton(parent: LinearLayout) {
        val grey = Color.rgb(236, 236, 236)
        val bar = { width: Int, height: Int ->
            View(this).apply { background = bg(grey, 6); layoutParams = LinearLayout.LayoutParams(if (width == 0) -1 else dp(width), dp(height)).apply { bottomMargin = dp(8) } }
        }
        space(parent, 8)
        repeat(4) {
            parent.addView(row().apply {
                gravity = Gravity.TOP
                addView(View(this@MainActivity).apply { background = bg(grey, 10) }, LinearLayout.LayoutParams(dp(64), dp(64)).apply { rightMargin = dp(14) })
                addView(column().apply { addView(bar(120, 14)); addView(bar(0, 12)); addView(bar(70, 12)) }, LinearLayout.LayoutParams(0, -2, 1f))
            }); space(parent, 20)
        }
    }
    /** The design's confirmation dialog: icon, question, a red button that does it and an outlined one that doesn't. */
    private fun confirm(art: String, title: String, message: String, yes: String, no: String, action: () -> Unit) {
        val box = column().apply { setPadding(dp(24), dp(28), dp(24), dp(24)); background = bg(Color.WHITE, 20) }
        val dialog = AlertDialog.Builder(this).setView(box).create()
        box.addView(FrameLayout(this).apply {
            background = bg(pink, 100)
            addView(Icon(this@MainActivity, art, red), FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(64), dp(64)).apply { gravity = Gravity.CENTER_HORIZONTAL }); space(box, 16)
        box.addView(text(title, 20f, ink, true).apply { gravity = Gravity.CENTER }); space(box, 8)
        box.addView(text(message, 15f, Color.GRAY).apply { gravity = Gravity.CENTER }); space(box, 20)
        box.addView(button(yes, red) { dialog.dismiss(); action() }); space(box, 12)
        box.addView(outline(no, green) { dialog.dismiss() })
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }
    /** One line of the order-status timeline; [tail] is the colour of the connector to the next step (null for the last). */
    private fun step(parent: LinearLayout, mark: String, color: Int, title: String, detail: String, titleColor: Int, detailColor: Int, tail: Int?) {
        parent.addView(row().apply {
            gravity = Gravity.TOP
            addView(column().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(Icon(this@MainActivity, mark, color), LinearLayout.LayoutParams(dp(22), dp(22)))
                if (tail != null) addView(View(this@MainActivity).apply { setBackgroundColor(tail) }, LinearLayout.LayoutParams(dp(2), dp(24)))
            }, LinearLayout.LayoutParams(dp(22), -2).apply { rightMargin = dp(12) })
            addView(column().apply { addView(text(title, 14f, titleColor, true)); addView(text(detail, 12f, detailColor)) }, LinearLayout.LayoutParams(0, -2, 1f))
        })
    }
    private fun keyboard(target: View) {
        target.requestFocus()
        target.post { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(target, InputMethodManager.SHOW_IMPLICIT) }
    }
    private fun localImage(res: Int, height: Int): ImageView = ImageView(this).apply {
        setImageResource(res); scaleType = ImageView.ScaleType.CENTER_CROP; background = bg(); clipToOutline = true
        layoutParams = LinearLayout.LayoutParams(-1, dp(height))
    }
    /** A photo from the server; shows a plain grey tile until it has downloaded (or if there is none). */
    private fun photo(url: String?, height: Int): ImageView = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP; background = bg(Color.rgb(238, 238, 238)); clipToOutline = true
        layoutParams = LinearLayout.LayoutParams(-1, dp(height))
        Images.load(this, url)
    }
    private fun summary(parent: LinearLayout, label: String, value: String, color: Int = green) {
        parent.addView(row().apply {
            addView(text(label, 16f), LinearLayout.LayoutParams(0, -2, 1f))
            addView(text(value, 16f, color, true).apply { gravity = Gravity.END })
        })
    }
    /** Centered message for empty, loading and error states. */
    private fun notice(parent: LinearLayout, title: String, detail: String, retry: (() -> Unit)? = null) {
        space(parent, 60)
        parent.addView(text(title, 20f, ink, true).apply { gravity = Gravity.CENTER }); space(parent, 10)
        parent.addView(text(detail, 14f, muted).apply { gravity = Gravity.CENTER })
        if (retry != null) { space(parent, 20); parent.addView(outline("Try again", green, retry)) }
    }
    private fun chips(parent: LinearLayout, values: List<String>, pills: Boolean = false, isOn: (String) -> Boolean, action: (String) -> Unit) {
        val strip = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val r = row()
        values.forEach { value ->
            val on = isOn(value)
            r.addView(text(value, 14f, if (on) Color.WHITE else if (pills) green else muted, on).apply {
                gravity = Gravity.CENTER; setPadding(dp(14), dp(9), dp(14), dp(9))
                background = if (pills) bg(if (on) green else Color.WHITE, 20, green) else bg(if (on) green else soft, 8)
                setOnClickListener { action(value) }
            }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) })
        }
        strip.addView(r); parent.addView(strip)
    }

    // ------------------------------------------------------------------ navigation

    private fun navigate(to: String) { history.addLast(screen); show(to) }

    @Deprecated("Native back navigation")
    override fun onBackPressed() {
        when {
            screen == "Onboarding" && slide > 0 -> { slide--; show("Onboarding") }
            screen == "Splash" || screen == "Onboarding" -> super.onBackPressed()
            screen == "Verifying" -> show("Verification")
            history.isNotEmpty() -> show(history.removeLast())
            signedIn && screen != "Home" -> show("Home")
            !signedIn && screen != "Login" -> show("Login")
            else -> super.onBackPressed()
        }
    }

    private fun redraw() { quiet = true; show(screen); quiet = false }

    private fun show(to: String) {
        // Redrawing the same screen (e.g. after tapping a chip) keeps the scroll position.
        val keepScroll = if (to == screen) scroll?.scrollY ?: 0 else 0
        generation++
        poller?.let(main::removeCallbacks); poller = null
        screen = to; scroll = null
        val dark = to == "Home" || to == "Splash" || to == "Onboarding"
        window.statusBarColor = if (to == "Onboarding") Color.BLACK else if (dark) this.dark else Color.WHITE
        window.navigationBarColor = if (android.os.Build.VERSION.SDK_INT >= 26) Color.WHITE else ink
        window.decorView.systemUiVisibility =
            (if (android.os.Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0) or
            (if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        root = column().apply { setBackgroundColor(Color.WHITE) }; setContentView(root)
        when (to) {
            "Home", "Explore", "Favorites" -> menu(to)
            "Details" -> details()
            "Cart", "Checkout" -> cartScreen(to == "Checkout")
            "Orders" -> ordersScreen()
            "Order" -> trackScreen()
            "Order Details" -> orderScreen()
            "Pay" -> payScreen()
            "Upload" -> uploadScreen()
            "Onboarding" -> onboarding()
            "Profile" -> profileScreen()
            "Personal Info" -> personal()
            "Payment Method" -> payments()
            "Login", "Register" -> auth(to)
            "Verification" -> verification()
            "Verifying" -> verifying()
            "Registered" -> registered()
            "Splash" -> splash()
            "Success" -> success()
            "Cafe" -> cafe()
        }
        if (keepScroll > 0) scroll?.let { s -> s.post { s.scrollTo(0, keepScroll) } }
    }

    private fun header(title: String, right: View? = null) {
        val h = row().apply { setPadding(dp(16), dp(12), dp(16), dp(12)) }
        h.addView(icon("back", ink) { onBackPressed() })
        h.addView(text(title, 17f, ink, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, dp(40), 1f))
        h.addView(right ?: View(this), LinearLayout.LayoutParams(dp(40), dp(40))); root.addView(h)
    }
    private fun content(): LinearLayout {
        val s = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        body = column().apply { setPadding(dp(24), dp(12), dp(24), dp(24)) }
        s.addView(body); root.addView(s, LinearLayout.LayoutParams(-1, 0, 1f)); scroll = s; return body
    }
    private fun footer(label: String, color: Int = green, above: View? = null, action: () -> Unit) {
        root.addView(column().apply {
            setPadding(dp(24), dp(16), dp(24), dp(20)); elevation = dp(8).toFloat(); setBackgroundColor(Color.WHITE)
            above?.let { addView(it) }
            addView(button(label, color, action))
        })
    }
    private fun nav(active: String) {
        val bar = row().apply { setPadding(dp(8), dp(7), dp(8), dp(8)); elevation = dp(8).toFloat(); setBackgroundColor(Color.WHITE) }
        listOf("Home", "Explore", "Orders", "Profile").forEach { name ->
            bar.addView(column().apply {
                gravity = Gravity.CENTER
                addView(icon(name.lowercase(), if (name == active) green else muted) { history.clear(); show(name) })
                addView(text(name, 11f, if (name == active) green else muted, name == active).apply { gravity = Gravity.CENTER })
                setOnClickListener { history.clear(); show(name) }
            }, LinearLayout.LayoutParams(0, dp(62), 1f))
        }; root.addView(bar)
    }

    // ------------------------------------------------------------------ menu

    /** Fetches categories and items; redraws the menu screens when something changed. */
    private fun refreshMenu(force: Boolean = false) {
        val stale = System.currentTimeMillis() - menuLoadedAt > MENU_MAX_AGE_MS
        if (menuLoading || !(force || stale) || (menuError != null && !force)) return
        menuLoading = true; menuError = null
        val onMenu = { screen in setOf("Home", "Explore", "Favorites") }
        load({ api.categories() to api.menuItems() }, sticky = true, failed = {
            menuLoading = false
            menuLoadedAt = System.currentTimeMillis()
            if (items.isEmpty()) { menuError = it; if (onMenu()) redraw() }
        }) { (newCategories, newItems) ->
            menuLoading = false
            menuLoadedAt = System.currentTimeMillis()
            val changed = newCategories != categories || newItems != items
            categories = newCategories; items = newItems
            if (categories.none { it.id == categoryId }) categoryId = 0
            if (changed && onMenu()) redraw()
        }
    }

    private fun menu(mode: String) {
        if (!quiet) refreshMenu()
        val grid = column()
        if (mode == "Home") {
            val h = column().apply { setPadding(dp(24), dp(20), dp(24), dp(14)); setBackgroundColor(Color.rgb(31, 31, 31)) }
            val loc = row(); val label = column(); label.addView(text("Location", 13f, muted)); space(label, 6)
            label.addView(text("Moni Coffee, Phnom Penh⌄", 15f, Color.WHITE, true)); label.setOnClickListener { navigate("Cafe") }
            loc.addView(label, LinearLayout.LayoutParams(0, -2, 1f)); loc.addView(icon("cart", Color.WHITE) { navigate("Cart") }); h.addView(loc); space(h, 14)
            val names = listOf("All") + categories.map { it.name }
            val pick = { name: String -> categoryId = categories.firstOrNull { it.name == name }?.id ?: 0; show("Home") }
            val searchRow = row()
            val search = field("Search coffee", query).apply { background = null; setTextColor(Color.WHITE); setPadding(dp(4), 0, dp(12), 0) }
            val searchBox = row().apply { background = bg(Color.rgb(43, 43, 43)); setPadding(dp(8), 0, 0, 0) }
            searchBox.addView(Icon(this, "explore", Color.rgb(200, 200, 200)), LinearLayout.LayoutParams(dp(32), dp(32)))
            searchBox.addView(search, LinearLayout.LayoutParams(0, dp(48), 1f))
            searchRow.addView(searchBox, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(16) })
            searchRow.addView(icon("filter", Color.WHITE) {
                AlertDialog.Builder(this).setTitle("Category").setItems(names.toTypedArray()) { _, i -> pick(names[i]) }.show()
            }.apply { background = bg(green); layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)) }); h.addView(searchRow); root.addView(h)
            val b = content()
            val current = categories.firstOrNull { it.id == categoryId }?.name ?: "All"
            chips(b, names, false, { it == current }, pick); space(b, 16)
            b.addView(grid)
            val visible = { items.filter { (categoryId == 0 || it.categoryId == categoryId) && it.name.contains(query, true) } }
            fillGrid(grid, visible())
            search.onChange { query = it; fillGrid(grid, visible()) }
            nav("Home")
        } else {
            if (mode == "Favorites") {
                header("My Favorites"); val b = content(); b.addView(grid)
                fillGrid(grid, items.filter { it.id in favorites })
                return
            }
            header("Explore", icon("heart") { navigate("Favorites") })
            val b = content(); val search = field("Search coffee"); b.addView(search)
            b.addView(grid)
            fillGrid(grid, items)
            search.onChange { typed -> fillGrid(grid, items.filter { it.name.contains(typed, true) }) }
            nav("Explore")
        }
    }

    private fun fillGrid(grid: LinearLayout, list: List<MenuItem>) {
        grid.removeAllViews()
        if (list.isEmpty()) {
            when {
                menuError != null -> problem(grid, menuError!!) { refreshMenu(force = true); redraw() }
                items.isEmpty() && menuLoading -> skeleton(grid)
                screen == "Favorites" -> empty(grid, "heart", "No favorite coffee yet", "When there's coffee, it will appear here", "Add Now") { history.clear(); show("Explore") }
                else -> notice(grid, "No items found", "Try another category or search.")
            }
            return
        }
        list.chunked(2).forEach { pair ->
            val r = row().apply { gravity = Gravity.TOP }
            pair.forEachIndexed { index, item ->
                val card = column().apply { setPadding(dp(8), dp(8), dp(8), dp(12)); background = bg(); elevation = dp(3).toFloat() }
                val picture = photo(item.imageUrl, 128).apply { if (!item.inStock) alpha = 0.45f }
                if (screen == "Favorites") card.addView(FrameLayout(this).apply {
                    addView(picture)
                    addView(Icon(this@MainActivity, "heartfill", Color.rgb(240, 80, 80)).apply {
                        contentDescription = "Remove from favorites"
                        setOnClickListener { favorites.remove(item.id); saveFavorites(); show("Favorites") }
                    }, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP or Gravity.END))
                }) else card.addView(picture)
                space(card, 8)
                card.addView(text(item.name, 16f, ink, true).apply { maxLines = 1 }); space(card, 4)
                card.addView(text(item.categoryName, 11f, muted)); space(card, 6)
                val price = row()
                price.addView(text(money(item.price), 19f, if (item.inStock) Color.BLACK else muted, true), LinearLayout.LayoutParams(0, -2, 1f))
                if (item.inStock) price.addView(button("+") { open(item) }.apply { minHeight = dp(32) }, LinearLayout.LayoutParams(dp(32), dp(32)))
                else price.addView(text("Out of stock", 11f, red, true))
                card.addView(price)
                card.setOnClickListener { open(item) }
                r.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index == 0) rightMargin = dp(16) })
            }
            if (pair.size == 1) r.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            grid.addView(r); space(grid, 16)
        }
    }

    // ------------------------------------------------------------------ item details

    private fun open(item: MenuItem) {
        selected = item; quantity = 1; picks.clear()
        // Free choices such as Size start on their first value; paid extras start off.
        item.options.filter { it.choices.isNotEmpty() && it.extraPrice == 0.0 }.forEach { picks[it.id] = it.choices.first() }
        navigate("Details")
    }

    private fun saveFavorites() = prefs.edit().putStringSet("favorites", favorites.map { it.toString() }.toSet()).apply()

    private fun details() {
        val item = selected ?: return show("Home")
        val saved = item.id in favorites
        header("Details", icon(if (saved) "heartfill" else "heart", if (saved) Color.rgb(240, 80, 80) else muted) {
            if (!favorites.add(item.id)) favorites.remove(item.id)
            saveFavorites(); show("Details")
        })
        val b = content()
        b.addView(FrameLayout(this).apply {
            addView(photo(item.imageUrl, 200).apply { if (!item.inStock) alpha = 0.55f })
            if (!item.inStock) addView(
                text("Out of Stock", 14f, Color.WHITE, true).apply { setPadding(dp(16), dp(7), dp(16), dp(8)); background = bg(Color.rgb(222, 68, 60), 20) },
                FrameLayout.LayoutParams(-2, -2, Gravity.CENTER),
            )
        }); space(b, 24)
        val title = row(); title.addView(text(item.name, 19f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        title.addView(text("♧  ${item.categoryName}", 13f, green)); b.addView(title)
        if (!item.inStock) {
            space(b, 10)
            b.addView(text("This item is currently unavailable. Please check back later.", 13f, red).apply {
                gravity = Gravity.CENTER; setPadding(dp(8), dp(6), dp(8), dp(6)); background = bg(pink, 8)
            })
        }
        if (item.description.isNotBlank()) {
            if (item.inStock) divider(b) else space(b, 16)
            b.addView(text("Description", 15f, ink, true)); space(b, 14)
            b.addView(text(item.description, 14f, Color.GRAY).apply { setLineSpacing(dp(4).toFloat(), 1f) })
        }
        space(b, 24)

        // Everything the customer chooses; greyed out and untappable when the item is out of stock.
        val controls = column()
        val choices = item.options.filter { it.choices.isNotEmpty() }
        // A short free choice (Size: Small / Medium / Large) is drawn as round letters next to Quantity.
        val size = choices.firstOrNull { o -> o.extraPrice == 0.0 && o.choices.size <= 4 && o.choices.map { it.first().uppercaseChar() }.distinct().size == o.choices.size }
        val addOns = item.options.filter { it.choices.isEmpty() }
        if (addOns.isNotEmpty()) {
            val label = { o: ItemOption -> if (o.extraPrice > 0) "${o.name} +${money(o.extraPrice)}" else o.name }
            controls.addView(text("Add-ons", 15f, ink, true)); space(controls, 12)
            chips(controls, addOns.map(label), true, { name -> addOns.first { label(it) == name }.id in picks }) { name ->
                val id = addOns.first { label(it) == name }.id
                if (picks.remove(id) == null) picks[id] = ""
                show("Details")
            }
            space(controls, 24)
        }
        choices.filter { it != size }.forEach { option ->
            val extra = if (option.extraPrice > 0) "  +${money(option.extraPrice)}" else ""
            controls.addView(text(option.name + extra, 15f, ink, true)); space(controls, 12)
            chips(controls, option.choices, true, { picks[option.id] == it }) { choice ->
                // A free choice always has a value; a paid one can be tapped off again.
                if (picks[option.id] == choice && option.extraPrice > 0) picks.remove(option.id) else picks[option.id] = choice
                show("Details")
            }
            space(controls, 24)
        }
        val bottomRow = row().apply { gravity = Gravity.TOP }
        if (size != null) {
            val left = column(); left.addView(text(size.name, 15f, ink, true)); space(left, 12)
            val letters = row()
            size.choices.forEach { choice ->
                val on = picks[size.id] == choice
                letters.addView(text(choice.take(1).uppercase(), 15f, if (on) Color.WHITE else muted, on).apply {
                    gravity = Gravity.CENTER; background = bg(if (on) green else Color.rgb(236, 236, 236), 100); contentDescription = choice
                    setOnClickListener { picks[size.id] = choice; show("Details") }
                }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(12) })
            }
            left.addView(letters); bottomRow.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val right = column(); right.addView(text("Quantity", 15f, ink, true)); space(right, 12)
        val q = row()
        q.addView(stepper("−", 36, true) { if (quantity > 1) quantity--; show("Details") })
        q.addView(text("$quantity", 16f).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(44), -2))
        q.addView(stepper("+", 36, true) { if (quantity < MAX_QUANTITY) quantity++; show("Details") })
        // Fixed width: a wrap-content column of full-width children would collapse to nothing.
        right.addView(q); bottomRow.addView(right, LinearLayout.LayoutParams(dp(116), -2))
        controls.addView(bottomRow)
        if (item.inStock) b.addView(controls) else b.addView(FrameLayout(this).apply {
            addView(controls.apply { alpha = 0.4f })
            addView(View(this@MainActivity).apply { isClickable = true }, FrameLayout.LayoutParams(-1, -1))
        })

        val entry = CartItem(item, picks.toMap(), quantity)
        val bottom = row().apply { setPadding(dp(24), dp(16), dp(24), dp(20)); elevation = dp(8).toFloat(); setBackgroundColor(Color.WHITE) }
        val price = column(); price.addView(text(money(entry.unitPrice * quantity), 25f, ink, true)); price.addView(text("Total Price", 13f, muted))
        bottom.addView(price, LinearLayout.LayoutParams(0, -2, 1f))
        val add = row().apply { gravity = Gravity.CENTER; background = bg(if (item.inStock) green else Color.rgb(228, 228, 228)) }
        if (item.inStock) {
            add.addView(Icon(this, "orders", Color.WHITE), LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(6) })
            add.addView(text("Add to Cart", 16f, Color.WHITE, true))
            add.setOnClickListener {
                val existing = cart.find { it.item.id == item.id && it.picks == entry.picks }
                if (existing != null) existing.count = minOf(MAX_QUANTITY, existing.count + quantity) else cart.add(entry)
                navigate("Cart")
            }
        } else add.addView(text("Out of Stock", 16f, muted, true))
        bottom.addView(add, LinearLayout.LayoutParams(dp(166), dp(56))); root.addView(bottom)
    }

    // ------------------------------------------------------------------ cart and checkout

    private fun total() = cart.sumOf { it.unitPrice * it.count }

    private fun cartScreen(checkout: Boolean) {
        if (cart.isEmpty()) {
            if (checkout) return onBackPressed()
            header("My Cart")
            empty(content(), "cart", "Your cart is empty", "Browse the menu and add your favorite drinks\nto get started.", "Browse Menu") { history.clear(); show("Home") }
            return
        }
        header(if (checkout) "Order Summary" else "My Cart", if (checkout) null else icon("trash", red) {
            AlertDialog.Builder(this).setTitle("Clear your cart?").setPositiveButton("Clear") { _, _ -> cart.clear(); show("Cart") }.setNegativeButton("Cancel", null).show()
        })
        val b = content()
        if (checkout) {
            if (qr == null) load({ api.qrPayment() }, failed = {}) { qr = it; if (it.enabled) redraw() }
            segmented(b, listOf("Dine in", "Pickup"), true, { (it == "Pickup") == pickup }) { pickup = it == "Pickup"; checkoutError = false; show("Checkout") }
            space(b, 24)
            val problem = text(if (pickup) "ⓘ  Pickup time is required for pickup orders." else "ⓘ  Table number is required for dine-in orders.", 13f, red).apply {
                setPadding(0, dp(8), 0, 0); visibility = if (checkoutError) View.VISIBLE else View.GONE
            }
            if (pickup) {
                b.addView(row().apply {
                    addView(Icon(this@MainActivity, "clock", ink), LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(6) })
                    addView(text("Time", 17f, ink, true))
                }); space(b, 12)
                val value = text(pickupTime?.let { "Today, ${clock12(it)}" } ?: "Choose a time", 15f, if (pickupTime == null) muted else ink, true).apply { setPadding(0, dp(4), 0, dp(2)) }
                b.addView(labelled("Expected Pickup Time", value, checkoutError).apply { setOnClickListener { choosePickupTime() } })
            } else {
                val table = EditText(this).apply {
                    hint = "Enter your table number"; setText(tableNumber); textSize = 15f; setTypeface(null, Typeface.BOLD)
                    setTextColor(ink); setHintTextColor(muted); setSingleLine(); background = null; setPadding(0, dp(4), 0, dp(2))
                    filters = arrayOf(InputFilter.LengthFilter(20))
                }
                val box = labelled("Table Number", table, checkoutError)
                table.onChange {
                    tableNumber = it
                    if (checkoutError && it.isNotBlank()) {
                        // Clear the error in place: redrawing the screen would close the keyboard.
                        checkoutError = false; problem.visibility = View.GONE
                        box.background = bg(Color.rgb(243, 243, 243), 12, green); (box.getChildAt(0) as TextView).setTextColor(muted)
                    }
                }
                b.addView(box)
            }
            b.addView(problem); divider(b)
        } else { b.addView(text("You have ${count(cart.sumOf { it.count })} in your cart", 15f, ink, true)); space(b, 24) }
        val heading = row(); heading.addView(text("Order Items", 17f, ink, checkout), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(text("+ Add More", 14f, green, true).apply { setOnClickListener { history.clear(); show("Home") } }); b.addView(heading); space(b, 20)
        cart.toList().forEach { item ->
            val r = row(); r.addView(photo(item.item.imageUrl, 54), LinearLayout.LayoutParams(dp(54), dp(54)).apply { rightMargin = dp(12) })
            val label = column(); label.addView(text(item.item.name, 15f, ink, true)); space(label, 4)
            label.addView(text(listOf(item.label, money(item.unitPrice)).filter { it.isNotEmpty() }.joinToString(" • "), 12f, muted))
            r.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(stepper("−", 28, false) { item.count--; if (item.count == 0) cart.remove(item); show(screen) })
            r.addView(text("${item.count}", 14f).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(30), -2))
            r.addView(stepper("+", 28, false) { if (item.count < MAX_QUANTITY) item.count++; show(screen) })
            b.addView(r); space(b, 20)
        }
        if (!checkout) {
            divider(b); summary(b, "Total", money(total()))
            footer("Checkout Now") { qr = null; checkoutError = false; navigate("Checkout") }
            return
        }
        b.addView(text("Payment Summary", 17f, ink, true)); space(b, 16)
        pair(b, "Subtotal", money(total())); divider(b); pair(b, "Total", money(total()), labelBold = true)

        val qrOn = qr?.enabled == true
        if (!qrOn) payment = "cash"
        val methods = if (qrOn) listOf("Cash" to "cash", "QR Payment" to "manual_qr") else listOf("Cash" to "cash")
        // The payment bar above the button: tap it to switch method when the shop offers more than cash.
        val paying = row().apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
            addView(column().apply {
                addView(text(methods.first { it.second == payment }.first, 15f, ink, true)); space(this, 2)
                addView(text(money(total()), 13f, green, true))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Icon(this@MainActivity, "down", ink), LinearLayout.LayoutParams(dp(30), dp(30)))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle("Payment method")
                    .setItems(methods.map { it.first }.toTypedArray()) { _, i -> payment = methods[i].second; show("Checkout") }.show()
            }
        }
        footer("Place Order", above = paying) {
            if (if (pickup) pickupTime == null else tableNumber.isBlank()) { checkoutError = true; show("Checkout") }
            else placeOrder()
        }
    }

    /** "14:05" -> "2:05 PM", for showing the chosen pickup time. */
    private fun clock12(time: String): String {
        val hour = time.take(2).toIntOrNull() ?: return time
        return "${if (hour % 12 == 0) 12 else hour % 12}:${time.takeLast(2)} ${if (hour < 12) "AM" else "PM"}"
    }

    private fun choosePickupTime() {
        val now = Calendar.getInstance().apply { add(Calendar.MINUTE, 15) }
        TimePickerDialog(this, { _, hour, minute ->
            pickupTime = String.format(Locale.US, "%02d:%02d", hour, minute); checkoutError = false; show("Checkout")
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
    }

    private fun placeOrder() {
        val lines = cart.map { NewOrderLine(it.item.id, it.count, it.picks) }
        val type = if (pickup) "pickup" else "dine_in"
        menuLoadedAt = 0 // stock changes either way: on success it drops, on failure ours was out of date
        submit({ api.placeOrder(type, tableNumber.trim(), pickupTime, payment, lines) }) { order ->
            cart.clear(); tableNumber = ""; pickupTime = null; checkoutError = false
            currentOrder = order; ordersByTab.clear(); orderTab = "active"
            history.clear()
            if (order.paymentMethod == "manual_qr") { history.addLast("Orders"); history.addLast("Order"); show("Pay") } else show("Success")
        }
    }

    private fun success() {
        val b = content(); space(b, 80); b.addView(picture("cupcheck", green, 230)); space(b, 36)
        b.addView(text("Order placed successfully 🥳", 20f, ink, true).apply { gravity = Gravity.CENTER }); space(b, 14)
        b.addView(text("Your coffee is being prepared, please wait...", 15f, muted).apply { gravity = Gravity.CENTER }); space(b, 40)
        b.addView(button("View Order") {
            history.clear()
            if (currentOrder == null) show("Orders") else { history.addLast("Orders"); show("Order") } // Track Order
        })
    }

    // ------------------------------------------------------------------ orders

    /** Text and background colour of an order's status badge, as in the design. */
    private fun statusPill(order: Order): TextView = when (order.status) {
        "pending" -> pill(order.statusLabel, Color.rgb(146, 100, 10), Color.rgb(255, 243, 205))
        "accepted" -> pill(order.statusLabel, Color.rgb(37, 84, 199), Color.rgb(224, 235, 255))
        "preparing" -> pill(order.statusLabel, Color.WHITE, green)
        "cancelled", "rejected" -> pill(order.statusLabel, red, pink)
        else -> pill(order.statusLabel, green, Color.rgb(219, 242, 230)) // ready, completed
    }

    private fun ordersScreen() {
        root.addView(text("My Orders", 17f, ink, true).apply { setPadding(dp(24), dp(22), dp(24), dp(10)) })
        val b = content()
        segmented(b, orderTabs.map { it.first }, false, { name -> orderTabs.first { it.first == name }.second == orderTab }) { name ->
            orderTab = orderTabs.first { it.first == name }.second; show("Orders")
        }
        space(b, 20)
        val tab = orderTab
        val reload = {
            ordersError = null
            load({ api.orders(tab) }, failed = { ordersError = it; redraw() }) {
                if (ordersByTab[tab] != it) { ordersByTab[tab] = it; redraw() }
            }
        }
        if (!quiet) reload()
        every(ORDER_POLL_MS) { if (ordersError == null) reload() }
        val list = ordersByTab[tab]
        when {
            list == null && ordersError != null -> problem(b, ordersError!!) { ordersError = null; show("Orders") }
            list == null -> skeleton(b)
            list.isEmpty() -> empty(b, "orders", "No order history yet", "When there's activity, it will appear here", "Shop Now") { history.clear(); show("Home") }
            else -> list.forEach { o ->
                val card = column().apply {
                    background = bg(Color.WHITE, 12, line); setPadding(dp(16), dp(16), dp(16), dp(16))
                    setOnClickListener { currentOrder = o; navigate("Order") }
                }
                card.addView(row().apply {
                    addView(text("Order #${o.number}", 15f, ink, true), LinearLayout.LayoutParams(0, -2, 1f)); addView(statusPill(o))
                }); space(card, 8)
                card.addView(text(o.place, 13f, Color.GRAY))
                card.addView(View(this).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(12) })
                card.addView(row().apply {
                    addView(text(count(o.itemCount), 13f, muted), LinearLayout.LayoutParams(0, -2, 1f)); addView(text(money(o.total), 16f, ink, true))
                })
                b.addView(card); space(b, 12)
            }
        }
        nav("Orders")
    }

    /** Track Order: where the order is in the shop's flow, refreshed every few seconds. */
    private fun trackScreen() {
        val o = currentOrder ?: return show("Orders")
        val refresh = { load({ api.order(o.id) }, failed = {}) { if (it != currentOrder) { currentOrder = it; redraw() } } }
        if (!quiet) refresh()
        every(ORDER_POLL_MS) { refresh() }

        header("Track Order"); val b = content()
        b.addView(row().apply {
            background = bg(mint, 12); setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(column().apply {
                addView(text("Order #${o.number}", 16f, ink, true)); space(this, 2); addView(text(o.place, 12f, Color.GRAY))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(statusPill(o))
        }); space(b, 22)
        b.addView(text("Order Status", 16f, ink, true)); space(b, 14)
        val dineIn = o.orderType == "dine_in"
        val time = o.createdAt.drop(11).take(5)
        val rejected = o.status == "rejected"
        if (rejected || o.status == "cancelled") {
            val stop = if (rejected) Color.rgb(222, 68, 60) else Color.GRAY
            step(b, "stepdone", green, "Pending", "Order received • $time", ink, muted, stop)
            step(b, "stepx", stop, o.statusLabel, if (rejected) "Declined by Moni Coffee" else "Cancelled by you", if (rejected) red else ink, muted, null)
            space(b, 16)
            b.addView(column().apply {
                setPadding(dp(14), dp(12), dp(14), dp(12)); background = if (rejected) bg(pink, 10, Color.rgb(240, 170, 165)) else bg(soft, 10, line)
                addView(text(if (rejected) "Reason from Moni Coffee" else "Cancelled by you", 12f, if (rejected) red else Color.GRAY, true)); space(this, 4)
                addView(text(
                    if (rejected) o.rejectionReason.ifBlank { "The shop couldn't take this order." }
                    else "You cancelled this order while it was still Pending. The items have been returned to stock.",
                    14f, ink,
                ))
            })
        } else {
            val steps = listOf(
                Triple("pending", "Pending", if (o.status == "pending") "Waiting for Moni Coffee to accept • $time" else "Order received • $time"),
                Triple("accepted", "Accepted", "Confirmed by Moni Coffee"),
                Triple("preparing", "Preparing", if (o.status == "preparing") "Our barista is making your drinks" else "Our barista makes your drinks"),
                Triple("ready", "Ready", if (dineIn) "We'll bring it to your table" else "We'll let you know when it's ready"),
                Triple("completed", "Completed", if (dineIn) "Enjoy your coffee at Table ${o.tableNumber}" else "Enjoy your coffee"),
            )
            val at = steps.indexOfFirst { it.first == o.status }
            steps.forEachIndexed { i, (_, title, detail) ->
                val done = i < at || o.status == "completed"
                val now = i == at && !done
                step(
                    b, if (done) "stepdone" else if (now) "stepnow" else "steptodo", if (done || now) green else Color.rgb(205, 205, 205),
                    title, detail, if (done || now) ink else muted, if (now) green else muted,
                    if (i == steps.lastIndex) null else if (i < at) green else line,
                )
            }
            if (o.status != "completed") { space(b, 6); b.addView(text("⟳  Updates automatically every ${ORDER_POLL_MS / 1000} seconds", 12f, muted).apply { setPadding(dp(34), 0, 0, 0) }) }
        }
        space(b, 18)
        b.addView(row().apply {
            background = bg(Color.WHITE, 12, line); setPadding(dp(16), dp(14), dp(16), dp(14)); setOnClickListener { navigate("Order Details") }
            addView(column().apply {
                addView(text("${count(o.itemCount)}  •  ${o.paymentMethodLabel}", 14f, ink, true)); space(this, 2)
                addView(text("View order details", 12f, green, true))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(text(money(o.total), 16f, ink, true))
        })

        val bottom = column().apply { setPadding(dp(24), dp(12), dp(24), dp(20)) }
        val active = o.status in setOf("pending", "accepted", "preparing", "ready")
        if (o.canUploadProof) {
            bottom.addView(button(if (o.paymentProofUrl == null) "Pay with KH QR" else "View QR / replace screenshot") { navigate("Pay") }); space(bottom, 12)
        }
        if (active) {
            bottom.addView(text(if (o.canCancel) "You can cancel until Moni Coffee accepts your order." else "You can only cancel while the order is Pending.", 12f, muted).apply { gravity = Gravity.CENTER }); space(bottom, 10)
            if (o.canCancel) bottom.addView(outline("Cancel Order", red) {
                confirm("warning", "Cancel this order?", "Order #${o.number} will be cancelled. This can't be undone.", "Yes, Cancel Order", "Keep Order") {
                    submit({ api.cancelOrder(o.id) }) { currentOrder = it; ordersByTab.clear(); menuLoadedAt = 0; redraw() }
                }
            }) else bottom.addView(text("Cancel Order", 16f, muted, true).apply { gravity = Gravity.CENTER; background = bg(Color.rgb(236, 236, 236)); minHeight = dp(52) })
        } else bottom.addView(button("Order Again") { history.clear(); show("Home") })
        root.addView(bottom)
    }

    /** Order Details: what was ordered and how it is paid. */
    private fun orderScreen() {
        val o = currentOrder ?: return show("Orders")
        val refresh = { load({ api.order(o.id) }, failed = {}) { if (it != currentOrder) { currentOrder = it; redraw() } } }
        if (!quiet) refresh()
        every(ORDER_POLL_MS) { refresh() }

        header("Order Details"); val b = content()
        b.addView(row().apply {
            addView(text("Order Summary", 17f, ink, true), LinearLayout.LayoutParams(0, -2, 1f)); addView(statusPill(o))
        }); space(b, 14)
        pair(b, "Order ID", "#${o.number}"); space(b, 10)
        pair(b, "Order Date", o.createdAt.replace("T", " ").take(16)); space(b, 10)
        pair(b, "Order Type", o.place); space(b, 10)
        pair(b, "Payment Method", o.paymentMethodLabel); space(b, 10)
        pair(b, "Payment Status", o.paymentStatusLabel)
        divider(b); b.addView(text("Order List", 17f, ink, true)); space(b, 16)
        o.items.forEach { item ->
            val r = row()
            r.addView(photo(item.imageUrl, 48), LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(12) })
            val label = column(); label.addView(text(item.name, 15f, ink, true)); space(label, 4)
            label.addView(text(listOf(item.options, money(item.unitPrice)).filter { it.isNotBlank() }.joinToString(" • "), 12f, muted))
            r.addView(label, LinearLayout.LayoutParams(0, -2, 1f)); r.addView(text("× ${item.quantity}", 14f, ink, true))
            b.addView(r); space(b, 16)
        }
        divider(b); b.addView(text("Payment Summary", 17f, ink, true)); space(b, 16)
        pair(b, "Subtotal", money(o.total)); divider(b); pair(b, "Total Payment", money(o.total), labelBold = true)
    }

    /** "Total to pay / $24.00 / Order #MC-0142" at the top of the two payment screens. */
    private fun amount(parent: LinearLayout, label: String, o: Order) {
        parent.addView(text(label, 13f, muted).apply { gravity = Gravity.CENTER })
        parent.addView(text(money(o.total), 30f, ink, true).apply { gravity = Gravity.CENTER })
        parent.addView(text("Order #${o.number}", 13f, ink).apply { gravity = Gravity.CENTER }); space(parent, 18)
    }
    private fun centered(parent: LinearLayout, view: View) { parent.addView(row().apply { gravity = Gravity.CENTER; addView(view) }) }
    private val amber = Color.rgb(146, 100, 10)
    private val cream = Color.rgb(255, 243, 205)

    /** Pay with KH QR: the shop's QR code; the proof of payment is sent from the next screen. */
    private fun payScreen() {
        val o = currentOrder ?: return show("Orders")
        if (!quiet) {
            load({ api.qrPayment() }, failed = {}) { if (it != qr) { qr = it; redraw() } }
            load({ api.order(o.id) }, failed = {}) { if (it != currentOrder) { currentOrder = it; redraw() } }
        }
        every(ORDER_POLL_MS) { load({ api.order(o.id) }, failed = {}) { if (it != currentOrder) { currentOrder = it; redraw() } } }

        header("Pay with KH QR"); val b = content()
        amount(b, "Total to pay", o)
        val shop = qr
        when {
            shop == null -> b.addView(text("Loading the shop's QR code…", 14f, muted).apply { gravity = Gravity.CENTER })
            shop.qrImageUrl == null -> b.addView(text("The shop's QR code isn't available right now. Please pay at the counter.", 14f, muted).apply { gravity = Gravity.CENTER })
            else -> b.addView(FrameLayout(this).apply {
                background = bg(Color.WHITE, 16); elevation = dp(4).toFloat(); setPadding(dp(14), dp(14), dp(14), dp(14))
                addView(photo(shop.qrImageUrl, 220).apply { scaleType = ImageView.ScaleType.FIT_CENTER; background = null })
            }, LinearLayout.LayoutParams(dp(248), dp(248)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(4); bottomMargin = dp(4) })
        }
        space(b, 16)
        centered(b, when {
            o.paymentStatus == "paid" -> pill("●  Payment confirmed", green, Color.rgb(219, 242, 230))
            !o.canUploadProof -> pill("●  No longer waiting for a payment", Color.GRAY, soft)
            o.paymentProofUrl != null -> pill("●  Screenshot sent · waiting for staff confirmation", amber, cream)
            else -> pill("●  Waiting for staff confirmation", amber, cream)
        })
        space(b, 14)
        b.addView(text(
            shop?.instructions?.ifBlank { null }
                ?: "Scan the KH QR code with your banking app to complete payment. Once paid, tap below to upload a screenshot as proof — our staff will verify it before confirming your order.",
            13f, muted,
        ).apply { gravity = Gravity.CENTER; setLineSpacing(dp(3).toFloat(), 1f) })
        if (shop != null && shop.qrImageUrl != null) {
            space(b, 16)
            if (shop.accountName.isNotBlank()) pair(b, "Account name", shop.accountName)
            if (shop.khrAccount.isNotBlank()) { space(b, 8); pair(b, "KHR account", shop.khrAccount) }
            if (shop.usdAccount.isNotBlank()) { space(b, 8); pair(b, "USD account", shop.usdAccount) }
        }
        if (o.canUploadProof) footer(if (o.paymentProofUrl == null) "I've Paid – Upload Screenshot" else "Replace Screenshot") {
            proofBytes = null; proofPreview = null; navigate("Upload")
        } else footer("View My Order") { history.clear(); history.addLast("Orders"); show("Order") }
    }

    /** Upload Screenshot: pick the transfer screenshot, check it, then send it for staff to verify. */
    private fun uploadScreen() {
        val o = currentOrder ?: return show("Orders")
        header("Upload Screenshot"); val b = content()
        amount(b, "Amount Paid", o)
        val preview = proofPreview
        val box = column().apply {
            gravity = Gravity.CENTER; setPadding(dp(16), dp(28), dp(16), dp(28))
            background = GradientDrawable().apply {
                setColor(Color.rgb(243, 247, 245)); cornerRadius = dp(12).toFloat(); setStroke(dp(2), green, dp(6).toFloat(), dp(4).toFloat())
            }
            if (preview == null) {
                addView(picture("upload", green, 40)); space(this, 10)
                addView(text("Tap to upload screenshot", 15f, ink, true).apply { gravity = Gravity.CENTER }); space(this, 4)
                addView(text("JPG or PNG", 12f, muted).apply { gravity = Gravity.CENTER })
            } else {
                addView(ImageView(this@MainActivity).apply { setImageBitmap(preview); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(-1, dp(220))); space(this, 10)
                addView(text("Tap to choose another", 12f, green, true).apply { gravity = Gravity.CENTER })
            }
            setOnClickListener {
                try {
                    startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), PICK_PROOF)
                } catch (e: Exception) { toast("No app available to choose a picture.") }
            }
        }
        b.addView(FrameLayout(this).apply {
            background = bg(Color.WHITE, 16); elevation = dp(3).toFloat(); setPadding(dp(10), dp(10), dp(10), dp(10)); addView(box)
        }, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(2); rightMargin = dp(2); topMargin = dp(4); bottomMargin = dp(4) })
        space(b, 28)
        centered(b, if (preview == null) pill("●  Screenshot required before confirmation", amber, cream) else pill("●  Screenshot ready to submit", green, Color.rgb(219, 242, 230)))
        space(b, 12)
        b.addView(text("Make sure the amount, date and time, and reference number are clearly visible in your screenshot.", 13f, muted).apply { gravity = Gravity.CENTER; setLineSpacing(dp(3).toFloat(), 1f) })
        footer("Submit for Verification", if (proofBytes == null) Color.rgb(200, 200, 200) else green) {
            val jpeg = proofBytes ?: return@footer toast("Choose your payment screenshot first.")
            submit({ api.uploadPaymentProof(o.id, jpeg) }) {
                currentOrder = it; ordersByTab.clear(); proofBytes = null; proofPreview = null
                toast("Screenshot sent. Staff will confirm your payment shortly.")
                history.clear(); history.addLast("Orders"); show("Order")
            }
        }
    }

    @Deprecated("Result of the picture chooser")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val picture: Uri = data?.data ?: return
        if (requestCode != PICK_PROOF || resultCode != RESULT_OK) return
        load({
            // Shrink to a JPEG well under the server's 2 MB limit; it is sent when the customer taps Submit.
            val bytes = contentResolver.openInputStream(picture)?.use { it.readBytes() }
            val bitmap = bytes?.let { Images.decode(it, 1600) } ?: throw ApiException(0, "That file couldn't be read as a picture.")
            bitmap to ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        }, sticky = true, failed = { alert(it.message) }) { (bitmap, jpeg) ->
            proofPreview = bitmap; proofBytes = jpeg
            if (screen == "Upload") redraw()
        }
    }

    // ------------------------------------------------------------------ onboarding

    private fun onboarding() {
        val slides = listOf(
            Triple(R.drawable.onboard1, "Each morning,\nit's a cup of Americano that brings me to life.", "Next"),
            Triple(R.drawable.onboard2, "On a weekend afternoon,\nit's a leisurely cappuccino.", "Next"),
            Triple(R.drawable.cafe, "When working late into the night,\nit's the life-saving latte.", "Start"),
        )
        val (image, title, label) = slides[slide.coerceIn(0, slides.lastIndex)]
        val frame = FrameLayout(this)
        frame.addView(ImageView(this).apply { setImageResource(image); scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1))
        frame.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.argb(70, 0, 0, 0), Color.argb(150, 0, 0, 0), Color.argb(250, 0, 0, 0)))
        }, FrameLayout.LayoutParams(-1, -1))
        val b = column().apply { setPadding(dp(24), 0, dp(24), dp(40)) }
        b.addView(text(title, 24f, Color.WHITE, true).apply { setLineSpacing(dp(4).toFloat(), 1f) }); space(b, 14)
        b.addView(text("No need to wait, no need to hesitate — just a simple tap is all it takes.", 14f, Color.rgb(210, 210, 210))); space(b, 32)
        val bars = row()
        slides.indices.forEach { i ->
            bars.addView(View(this).apply { background = bg(if (i == slide) Color.rgb(0, 150, 105) else Color.rgb(130, 130, 130), 4) },
                LinearLayout.LayoutParams(0, dp(4), if (i == slide) 4f else 1f).apply { if (i > 0) leftMargin = dp(8) })
        }
        b.addView(bars); space(b, 28)
        b.addView(button(label) {
            if (slide < slides.lastIndex) { slide++; show("Onboarding") }
            else { prefs.edit().putBoolean("onboarded", true).apply(); show("Login") }
        })
        frame.addView(b, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    // ------------------------------------------------------------------ profile

    private fun displayName() = profile?.fullName?.takeIf { it.isNotBlank() } ?: "Moni Coffee Guest"
    private fun initials() = displayName().split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")

    private fun profileScreen() {
        if (!quiet) load({ api.me() }, failed = {}) { if (it != profile) { saveProfile(it); redraw() } }
        val b = content(); space(b, 42); val identity = row()
        identity.addView(text(initials(), 32f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = bg(Color.rgb(22, 100, 74), 100) }, LinearLayout.LayoutParams(dp(76), dp(76)).apply { rightMargin = dp(20) })
        identity.addView(
            column().apply { addView(text(displayName(), 22f, ink, true)); space(this, 6); addView(text(profile?.phone ?: "", 15f, muted)) },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        b.addView(identity); space(b, 40); b.addView(text("Basic Info", 20f, ink, true)); space(b, 12)
        listOf("Order History" to "Orders", "Personal Info" to "Personal Info", "Payment Method" to "Payment Method", "My Favorites" to "Favorites").forEach { (label, target) ->
            b.addView(row().apply {
                setPadding(0, dp(4), 0, dp(4)); setOnClickListener { navigate(target) }
                addView(text(label, 17f), LinearLayout.LayoutParams(0, -2, 1f))
                addView(Icon(this@MainActivity, "chevron", muted), LinearLayout.LayoutParams(dp(32), dp(32)))
            }); divider(b)
        }
        b.addView(row().apply {
            addView(text("App Version", 17f), LinearLayout.LayoutParams(0, -2, 1f)); addView(text("1.0", 17f, muted))
        }); divider(b)
        footer("Log Out", red) { confirmLogout() }; nav("Profile")
    }

    private fun confirmLogout() = confirm("logout", "Log out?", "You'll need to log in again to place or track your orders.", "Yes, Log Out", "Stay Logged In") {
        // Sign out on this phone even if the server can't be reached to drop the token.
        submit({ try { api.logout() } catch (e: ApiException) { } }) { signOut() }
    }

    private fun personal() {
        header("Personal Info"); val b = content(); space(b, 24)
        b.addView(text(initials(), 40f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = bg(green, 100); layoutParams = LinearLayout.LayoutParams(dp(120), dp(120)).apply { gravity = Gravity.CENTER_HORIZONTAL } }); space(b, 48)
        b.addView(text("Full name", 13f, muted)); val name = field("Full name", profile?.fullName ?: ""); b.addView(name)
        b.addView(text("Address", 13f, muted)); val address = field("Address", profile?.address ?: ""); b.addView(address)
        b.addView(text("Phone number", 13f, muted))
        b.addView(field("Phone number", profile?.phone ?: "", true).apply { isEnabled = false; setTextColor(muted); background = bg(soft, 12, line) })
        b.addView(text("Your phone number is your login, so it can't be changed here.", 12f, muted))
        footer("Save") {
            if (name.text.isBlank()) name.error = "Enter your name"
            else submit({ api.updateMe(name.text.toString().trim(), address.text.toString().trim()) }) {
                saveProfile(it); toast("Saved"); onBackPressed()
            }
        }
    }

    /** The customer's preferred payment method, pre-selected at checkout. */
    private fun payments() {
        if (!quiet) load({ api.qrPayment() }, failed = {}) { if (it != qr) { qr = it; redraw() } }
        header("Payment method"); val b = content(); b.addView(text("Payment Method", 20f, ink, true)); space(b, 20)
        val qrOn = qr?.enabled == true
        listOf("cash" to "Cash", "manual_qr" to "Manual QR Payment").forEach { (value, label) ->
            val usable = value == "cash" || qrOn
            val chosen = value == payment && usable
            val tile = if (value == "cash") FrameLayout(this).apply {
                background = bg(Color.WHITE, 6, line)
                addView(Icon(this@MainActivity, "cash", ink), FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
            } else text("QR", 12f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = bg(green, 6) }
            b.addView(row().apply {
                if (!usable) alpha = 0.45f
                addView(tile, LinearLayout.LayoutParams(dp(56), dp(40)).apply { rightMargin = dp(16) })
                addView(text(label, 17f), LinearLayout.LayoutParams(0, -2, 1f))
                if (chosen) addView(Icon(this@MainActivity, "check", green), LinearLayout.LayoutParams(dp(32), dp(32)))
                if (usable) setOnClickListener { payment = value; prefs.edit().putString("payment", value).apply(); show("Payment Method") }
            })
            if (!usable) { space(b, 6); b.addView(text("Not offered by the shop at the moment.", 13f, muted)) }
            divider(b)
        }
    }

    // ------------------------------------------------------------------ login and registration

    private fun auth(mode: String) {
        val register = mode == "Register"
        val b = content(); space(b, 70)
        b.addView(text("Moni Coffee", 32f, ink, true).apply { gravity = Gravity.CENTER }); space(b, 12)
        b.addView(text("Enter your information below.", 14f, muted).apply { gravity = Gravity.CENTER }); space(b, 52)
        val name = field("Full name", authName); if (register) b.addView(name)
        val number = field("Phone number", authPhone, numeric = true); b.addView(number)
        space(b, 40)
        if (register) { b.addView(text("By signing up, you agree to our Terms and Conditions and our Privacy Policy.", 14f, muted).apply { gravity = Gravity.CENTER }); space(b, 16) }
        val ready = { number.text.isNotBlank() && (!register || name.text.isNotBlank()) }
        val go = button(if (register) "Sign up" else "Log in") {
            if (ready()) requestCode(if (register) "register" else "login", number.text.toString().trim(), name.text.toString().trim()) { navigate("Verification") }
        }
        // Grey until the form is filled in, as in the design.
        val paint = { go.background = bg(if (ready()) green else Color.rgb(228, 228, 228)) }
        paint(); number.onChange { paint() }; name.onChange { paint() }
        b.addView(go); space(b, 24)
        b.addView(text("Server: ${api.baseUrl}  (change)", 12f, muted).apply { gravity = Gravity.CENTER; setOnClickListener { chooseServer() } })

        val question = if (register) "Already have an account? " else "Don’t have an account? "
        val link = SpannableString(question + if (register) "Log in" else "Sign up").apply {
            setSpan(ForegroundColorSpan(green), question.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), question.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        root.addView(text("", 14f).apply {
            text = link; gravity = Gravity.CENTER; setPadding(dp(24), dp(16), dp(24), dp(28))
            setOnClickListener { history.clear(); show(if (register) "Login" else "Register") }
        })
    }

    /** The code is typed into an invisible field and shown in the four boxes of the design. */
    private fun verification() {
        val t = ticket ?: return show("Login")
        header("OTP Verification"); val b = content(); space(b, 12)
        b.addView(text("Enter the $CODE_LENGTH-digit code sent to $authPhone.", 14f, muted).apply { setLineSpacing(dp(3).toFloat(), 1f) }); space(b, 20)
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; filters = arrayOf(InputFilter.LengthFilter(CODE_LENGTH))
            alpha = 0f; isCursorVisible = false; background = null
        }
        val boxes = row()
        val digits = (0 until CODE_LENGTH).map { i ->
            text("", 22f, green, true).apply { gravity = Gravity.CENTER; background = bg(Color.WHITE, 12, green) }.also {
                boxes.addView(it, LinearLayout.LayoutParams(0, dp(56), 1f).apply { if (i > 0) leftMargin = dp(20) })
            }
        }
        boxes.setOnClickListener { keyboard(input) }
        b.addView(boxes); b.addView(input, LinearLayout.LayoutParams(dp(1), dp(1))); space(b, 14)
        val timer = text("", 13f, Color.GRAY); b.addView(timer); space(b, 12)
        // The backend only includes the code in its reply while it runs in DEBUG mode (no SMS gateway yet).
        t.debugCode?.let { b.addView(text("Demo server: your code is $it", 13f, green, true)); space(b, 12) }
        b.addView(text("Resend code", 14f, green, true).apply {
            setOnClickListener { requestCode(authPurpose, authPhone, authName) { toast("A new code was sent."); show("Verification") } }
        })
        space(b, 40)
        val go = button("Verify") { if (input.text.length == CODE_LENGTH) { authCode = input.text.toString(); show("Verifying") } }
        go.background = bg(Color.rgb(228, 228, 228)); b.addView(go)
        input.onChange { typed ->
            digits.forEachIndexed { i, box -> box.text = typed.getOrNull(i)?.toString() ?: "" }
            go.background = bg(if (typed.length == CODE_LENGTH) green else Color.rgb(228, 228, 228))
        }
        keyboard(input)

        var ticks = 0
        val tick = {
            val left = t.expiresIn - ((System.currentTimeMillis() - ticketAt) / 1000).toInt()
            timer.text = if (left > 0) String.format(Locale.US, "Expires in %02d:%02d", left / 60, left % 60) else "This code has expired. Tap Resend code."
            // The shop's SMS device can confirm the code for us; pick that up without typing.
            if (++ticks % (OTP_POLL_MS / 1000).toInt() == 0 && left > 0) load({ api.otpStatus(authPurpose, t.requestId) }, failed = {}) { it?.let(::signIn) }
        }
        tick(); every(1000) { tick() }
    }

    private fun verifying() {
        header("Verifying"); val b = content(); space(b, 140)
        b.addView(ProgressBar(this).apply { indeterminateTintList = ColorStateList.valueOf(green) }, LinearLayout.LayoutParams(dp(64), dp(64)).apply { gravity = Gravity.CENTER_HORIZONTAL }); space(b, 24)
        b.addView(text("Verifying your number...", 18f, ink, true).apply { gravity = Gravity.CENTER }); space(b, 10)
        b.addView(text("Please wait while we confirm your code.\nThis may take a few seconds.", 14f, muted).apply { gravity = Gravity.CENTER })
        root.addView(text("Cancel", 15f, muted, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(24), dp(16), dp(24), dp(40)); setOnClickListener { show("Verification") }
        })
        // sticky: a code the server accepted signs the app in even if Cancel was tapped meanwhile.
        if (!quiet) load({ api.verifyOtp(authPurpose, authPhone, authCode) }, sticky = true, failed = {
            if (screen == "Verifying") show("Verification"); alert(it.message)
        }) { signIn(it) }
    }

    private fun registered() {
        val b = content(); space(b, 80); b.addView(picture("cup", green, 230)); space(b, 36)
        b.addView(text("Sign-up successful 🥳", 20f, ink, true).apply { gravity = Gravity.CENTER }); space(b, 14)
        b.addView(text("Congratulations! Your account has been activated. You can now explore and order from our menu.", 15f, muted).apply { gravity = Gravity.CENTER; setLineSpacing(dp(4).toFloat(), 1f) }); space(b, 32)
        b.addView(button("Get started") { history.clear(); show("Home") })
    }

    private fun splash() {
        root.setBackgroundColor(dark); root.gravity = Gravity.CENTER
        val title = SpannableString("MoniCoffee").apply { setSpan(ForegroundColorSpan(Color.rgb(0, 150, 105)), 4, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        root.addView(text("", 34f, Color.WHITE).apply { text = title; gravity = Gravity.CENTER }); space(root, 14)
        root.addView(text("Moments of Healing for City Dwellers", 14f, Color.rgb(190, 190, 190)).apply { gravity = Gravity.CENTER })
    }

    private fun requestCode(purpose: String, phone: String, fullName: String, then: () -> Unit) {
        submit({ api.requestOtp(purpose, phone, fullName.takeIf { purpose == "register" }) }) {
            authPurpose = purpose; authPhone = phone; authName = fullName; ticket = it; ticketAt = System.currentTimeMillis()
            then()
        }
    }

    /** Where the Django backend runs: 10.0.2.2 is "this computer" from the emulator; a real phone needs the PC's Wi-Fi address. */
    private fun chooseServer() {
        val input = field("http://192.168.1.10:8000", api.baseUrl).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        val box = column().apply { setPadding(dp(24), dp(16), dp(24), 0); addView(input) }
        AlertDialog.Builder(this).setTitle("Server address").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val typed = input.text.toString().trim().trimEnd('/')
                val url = when {
                    typed.isEmpty() -> DEFAULT_SERVER
                    typed.startsWith("http://") || typed.startsWith("https://") -> typed
                    else -> "http://$typed"
                }
                api.baseUrl = url; prefs.edit().putString("server", url).apply()
                menuLoadedAt = 0; menuError = null; show(screen)
            }.setNeutralButton("Reset") { _, _ ->
                api.baseUrl = DEFAULT_SERVER; prefs.edit().remove("server").apply(); show(screen)
            }.setNegativeButton("Cancel", null).show()
    }

    // ------------------------------------------------------------------ cafe

    private fun cafe() {
        header("Moni Cafe"); val b = content(); b.addView(localImage(R.drawable.cafe, 200)); space(b, 24)
        b.addView(text("Moni Coffee, Phnom Penh", 21f, ink, true)); space(b, 12); b.addView(text("◷  07:00–23:00", 14f, muted)); divider(b)
        b.addView(text("Location Details", 16f, green, true)); space(b, 24); b.addView(text("About", 18f, ink, true)); space(b, 12)
        b.addView(text("A cozy place to enjoy your favorite coffee, freshly prepared for dine-in or pickup.", 15f, Color.GRAY))
        footer("Browse Menu") { history.clear(); show("Home") }
    }

    private class Icon(context: android.content.Context, val kind: String, val tint: Int) : View(context) {
        override fun onDraw(c: Canvas) {
            super.onDraw(c); val s = width / 40f; c.save(); c.scale(s, s)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; strokeWidth = 2f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
            fun path(vararg points: Float) { val a = Path(); a.moveTo(points[0], points[1]); for (i in 2 until points.size step 2) a.lineTo(points[i], points[i + 1]); c.drawPath(a, p) }
            when (kind) {
                "home" -> { path(10f, 19f, 20f, 10f, 30f, 19f, 30f, 31f, 23f, 31f, 23f, 23f, 17f, 23f, 17f, 31f, 10f, 31f, 10f, 19f) }
                "explore" -> { c.drawCircle(18f, 17f, 8f, p); path(24f, 23f, 32f, 31f) }
                "profile" -> { c.drawCircle(20f, 20f, 11f, p); c.drawCircle(20f, 16f, 4f, p); c.drawArc(12f, 21f, 28f, 35f, 195f, 150f, false, p) }
                "cart" -> { path(7f, 10f, 11f, 10f, 14f, 25f, 28f, 25f, 31f, 14f, 12f, 14f); c.drawCircle(16f, 30f, 2f, p); c.drawCircle(26f, 30f, 2f, p) }
                "chevron" -> path(16f, 12f, 24f, 20f, 16f, 28f)
                "down" -> path(12f, 16f, 20f, 24f, 28f, 16f)
                "check" -> path(11f, 21f, 17f, 27f, 29f, 14f)
                "logout" -> { path(18f, 9f, 10f, 9f, 10f, 31f, 18f, 31f); path(17f, 20f, 31f, 20f); path(26f, 15f, 31f, 20f, 26f, 25f) }
                "cash" -> { c.drawRoundRect(6f, 12f, 34f, 28f, 3f, 3f, p); c.drawCircle(20f, 20f, 4f, p) }
                "clock" -> { c.drawCircle(20f, 20f, 11f, p); path(20f, 13f, 20f, 20f, 25f, 23f) }
                "heartfill" -> { p.style = Paint.Style.FILL; val a = Path(); a.moveTo(20f, 31f); a.cubicTo(0f, 18f, 12f, 5f, 20f, 14f); a.cubicTo(28f, 5f, 40f, 18f, 20f, 31f); c.drawPath(a, p) }
                "offline" -> {
                    c.drawArc(5f, 11f, 35f, 41f, 228f, 84f, false, p); c.drawArc(11f, 18f, 29f, 36f, 228f, 84f, false, p)
                    c.drawCircle(20f, 30f, 1.2f, p); path(9f, 8f, 31f, 33f)
                }
                "warning" -> { path(20f, 8f, 33f, 31f, 7f, 31f, 20f, 8f); path(20f, 17f, 20f, 24f); c.drawCircle(20f, 27.5f, 0.6f, p) }
                "upload" -> { path(20f, 27f, 20f, 9f); path(13f, 16f, 20f, 9f, 27f, 16f); path(8f, 24f, 8f, 32f, 32f, 32f, 32f, 24f) }
                "stepdone", "stepx" -> {
                    p.style = Paint.Style.FILL; c.drawCircle(20f, 20f, 18f, p)
                    p.style = Paint.Style.STROKE; p.color = Color.WHITE; p.strokeWidth = 3.5f
                    if (kind == "stepdone") path(12f, 20.5f, 17.5f, 26f, 28f, 14.5f) else { path(14f, 14f, 26f, 26f); path(26f, 14f, 14f, 26f) }
                }
                "stepnow" -> { p.strokeWidth = 3.5f; c.drawCircle(20f, 20f, 16f, p); p.style = Paint.Style.FILL; c.drawCircle(20f, 20f, 7.5f, p) }
                "steptodo" -> { p.strokeWidth = 3f; c.drawCircle(20f, 20f, 16f, p) }
                "cup", "cupcheck" -> {
                    // The paper cup from the design's success screens, on its pale green blob.
                    val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
                    fun shape(color: Int, vararg points: Float) {
                        val a = Path(); a.moveTo(points[0], points[1]); for (i in 2 until points.size step 2) a.lineTo(points[i], points[i + 1]); a.close()
                        f.color = color; c.drawPath(a, f)
                    }
                    f.color = Color.rgb(200, 245, 220); c.drawCircle(21f, 20f, 16f, f)
                    shape(Color.rgb(246, 246, 246), 12f, 12f, 28f, 12f, 26f, 34f, 14f, 34f)
                    shape(tint, 12.5f, 18f, 27.5f, 18f, 26.7f, 26f, 13.3f, 26f)
                    shape(tint, 11f, 9f, 29f, 9f, 29f, 12.5f, 11f, 12.5f)
                    shape(tint, 12.5f, 7f, 27.5f, 7f, 27.5f, 9f, 12.5f, 9f)
                    p.color = Color.WHITE; p.strokeWidth = 1.3f
                    if (kind == "cupcheck") path(17f, 22f, 19.3f, 24.2f, 23.2f, 19.8f) else c.drawOval(18.2f, 19.5f, 21.8f, 24.5f, p)
                }
                "orders" -> { path(11f, 16f, 29f, 16f, 31f, 32f, 9f, 32f, 11f, 16f); c.drawArc(16f, 8f, 24f, 23f, 180f, 180f, false, p) }
                "back" -> { path(23f, 12f, 15f, 20f, 23f, 28f); path(15f, 20f, 30f, 20f) }
                "trash" -> { path(13f, 14f, 14f, 31f, 26f, 31f, 27f, 14f); path(11f, 12f, 29f, 12f); path(17f, 9f, 23f, 9f) }
                "heart" -> { val a = Path(); a.moveTo(20f, 31f); a.cubicTo(0f, 18f, 12f, 5f, 20f, 14f); a.cubicTo(28f, 5f, 40f, 18f, 20f, 31f); c.drawPath(a, p) }
                else -> { path(10f, 13f, 30f, 13f); path(10f, 26f, 30f, 26f); c.drawCircle(17f, 13f, 3f, p); c.drawCircle(25f, 26f, 3f, p) }
            }; c.restore()
        }
    }

    private companion object {
        /** The Android emulator reaches the development computer's localhost at 10.0.2.2. */
        const val DEFAULT_SERVER = "http://10.0.2.2:8000"
        const val CODE_LENGTH = 4 // PhoneVerification.CODE_LENGTH on the backend
        const val MAX_QUANTITY = 50 // the backend's limit per order line
        const val MENU_MAX_AGE_MS = 30_000L
        const val ORDER_POLL_MS = 15_000L
        const val OTP_POLL_MS = 3_000L
        const val PICK_PROOF = 41
        const val SPLASH_MS = 1200L
    }
}
