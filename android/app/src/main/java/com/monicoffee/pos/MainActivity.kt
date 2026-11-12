package com.monicoffee.pos

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
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

    // --- menu ---
    private var categories = listOf<Category>()
    private var items = listOf<MenuItem>()
    private var menuLoadedAt = 0L
    private var menuLoading = false
    private var menuError: String? = null
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
    private var payment = "cash"
    private var qr: QrPayment? = null

    // --- orders ---
    private val orderTabs = listOf("In Progress" to "active", "Completed" to "completed", "Cancelled" to "cancelled")
    private var orderTab = "active"
    private val ordersByTab = mutableMapOf<String, List<Order>>()
    private var ordersError: String? = null
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
        show(if (signedIn) "Home" else "Login")
    }

    override fun onStop() {
        super.onStop()
        poller?.let(main::removeCallbacks)
    }

    override fun onRestart() {
        super.onRestart()
        // Restart polling and refresh what's on screen — except forms, which would lose what was typed.
        if (screen !in setOf("Login", "Register", "Personal Info")) show(screen)
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
        show("Home")
        toast(if (session.profile.fullName.isBlank()) "Welcome!" else "Welcome, ${session.profile.fullName}!")
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
    private fun button(label: String, action: () -> Unit): TextView = text(label, 16f, Color.WHITE, true).apply {
        val pad = if (label.length == 1) 0 else 1
        gravity = Gravity.CENTER; background = bg(green); minHeight = dp(56)
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
    private fun chips(parent: LinearLayout, values: List<String>, isOn: (String) -> Boolean, action: (String) -> Unit) {
        val strip = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val r = row()
        values.forEach { value ->
            val on = isOn(value)
            r.addView(text(value, 14f, if (on) Color.WHITE else muted, on).apply {
                gravity = Gravity.CENTER; setPadding(dp(14), dp(9), dp(14), dp(9)); background = bg(if (on) green else soft, 8)
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
        val dark = to == "Home"
        window.statusBarColor = if (dark) Color.rgb(31, 31, 31) else Color.WHITE
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
            "Order" -> orderScreen()
            "Pay" -> payScreen()
            "Profile" -> profileScreen()
            "Personal Info" -> personal()
            "Payment Method" -> payments()
            "Login", "Register", "Verification" -> auth(to)
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
    private fun footer(label: String, action: () -> Unit) {
        root.addView(column().apply {
            setPadding(dp(24), dp(16), dp(24), dp(20)); elevation = dp(8).toFloat(); setBackgroundColor(Color.WHITE)
            addView(button(label, action))
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
            if (items.isEmpty()) { menuError = it.message; if (onMenu()) redraw() }
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
            val search = field("Search coffee", query).apply { background = bg(Color.rgb(43, 43, 43)); setTextColor(Color.WHITE) }
            searchRow.addView(search, LinearLayout.LayoutParams(0, dp(48), 1f))
            searchRow.addView(icon("filter", Color.WHITE) {
                AlertDialog.Builder(this).setTitle("Category").setItems(names.toTypedArray()) { _, i -> pick(names[i]) }.show()
            }); h.addView(searchRow); root.addView(h)
            val b = content()
            val current = categories.firstOrNull { it.id == categoryId }?.name ?: "All"
            chips(b, names, { it == current }, pick); space(b, 16)
            b.addView(grid)
            val visible = { items.filter { (categoryId == 0 || it.categoryId == categoryId) && it.name.contains(query, true) } }
            fillGrid(grid, visible())
            search.onChange { query = it; fillGrid(grid, visible()) }
            nav("Home")
        } else {
            header(if (mode == "Favorites") "My Favorites" else "Explore", icon("heart") { if (mode != "Favorites") navigate("Favorites") })
            val b = content(); val search = field("Search coffee"); b.addView(search)
            b.addView(grid)
            val available = if (mode == "Favorites") items.filter { it.id in favorites } else items
            fillGrid(grid, available)
            search.onChange { typed -> fillGrid(grid, available.filter { it.name.contains(typed, true) }) }
            nav("Explore")
        }
    }

    private fun fillGrid(grid: LinearLayout, list: List<MenuItem>) {
        grid.removeAllViews()
        if (list.isEmpty()) {
            when {
                menuError != null -> notice(grid, "Couldn't load the menu", menuError!!) { refreshMenu(force = true); redraw() }
                items.isEmpty() && menuLoading -> notice(grid, "Loading menu…", "Fetching today's menu from the shop.")
                screen == "Favorites" -> notice(grid, "No favorites yet", "Tap the heart on an item to save it here.")
                else -> notice(grid, "No items found", "Try another category or search.")
            }
            return
        }
        list.chunked(2).forEach { pair ->
            val r = row().apply { gravity = Gravity.TOP }
            pair.forEachIndexed { index, item ->
                val card = column().apply { setPadding(dp(8), dp(8), dp(8), dp(12)); background = bg(); elevation = dp(3).toFloat() }
                card.addView(photo(item.imageUrl, 128).apply { if (!item.inStock) alpha = 0.45f }); space(card, 8)
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

    private fun details() {
        val item = selected ?: return show("Home")
        val saved = item.id in favorites
        header("Details", icon("heart", if (saved) Color.RED else muted) {
            if (!favorites.add(item.id)) favorites.remove(item.id)
            prefs.edit().putStringSet("favorites", favorites.map { it.toString() }.toSet()).apply()
            show("Details")
        })
        val b = content(); b.addView(photo(item.imageUrl, 200)); space(b, 24)
        val title = row(); title.addView(text(item.name, 19f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        title.addView(text("♧  ${item.categoryName}", 13f, green)); b.addView(title)
        if (!item.inStock) { space(b, 6); b.addView(text("Out of stock", 13f, red, true)) }
        if (item.description.isNotBlank()) {
            divider(b); b.addView(text("Description", 15f, ink, true)); space(b, 14)
            b.addView(text(item.description, 14f, Color.GRAY).apply { setLineSpacing(dp(4).toFloat(), 1f) })
        }
        space(b, 24)
        item.options.filter { it.choices.isNotEmpty() }.forEach { option ->
            val extra = if (option.extraPrice > 0) "  +${money(option.extraPrice)}" else ""
            b.addView(text(option.name + extra, 15f, ink, true)); space(b, 12)
            chips(b, option.choices, { picks[option.id] == it }) { choice ->
                // A free choice (Size) always has a value; a paid one can be tapped off again.
                if (picks[option.id] == choice && option.extraPrice > 0) picks.remove(option.id) else picks[option.id] = choice
                show("Details")
            }
            space(b, 24)
        }
        val addOns = item.options.filter { it.choices.isEmpty() }
        if (addOns.isNotEmpty()) {
            val label = { o: ItemOption -> if (o.extraPrice > 0) "${o.name} +${money(o.extraPrice)}" else o.name }
            b.addView(text("Add-ons", 15f, ink, true)); space(b, 12)
            chips(b, addOns.map(label), { name -> addOns.first { label(it) == name }.id in picks }) { name ->
                val id = addOns.first { label(it) == name }.id
                if (picks.remove(id) == null) picks[id] = ""
                show("Details")
            }
            space(b, 24)
        }
        b.addView(text("Quantity", 15f, ink, true)); space(b, 12)
        val q = row()
        q.addView(button("−") { if (quantity > 1) quantity--; show("Details") }.apply { minHeight = dp(36) }, LinearLayout.LayoutParams(dp(36), dp(36)))
        q.addView(text("  $quantity  ", 17f))
        q.addView(button("+") { if (quantity < MAX_QUANTITY) quantity++; show("Details") }.apply { minHeight = dp(36) }, LinearLayout.LayoutParams(dp(36), dp(36)))
        b.addView(q)

        val entry = CartItem(item, picks.toMap(), quantity)
        val bottom = row().apply { setPadding(dp(24), dp(16), dp(24), dp(20)); elevation = dp(8).toFloat(); setBackgroundColor(Color.WHITE) }
        val price = column(); price.addView(text(money(entry.unitPrice * quantity), 25f, ink, true)); price.addView(text("Total Price", 13f, muted))
        bottom.addView(price, LinearLayout.LayoutParams(0, -2, 1f))
        val add = button(if (item.inStock) "Add to Cart" else "Out of Stock") {
            val existing = cart.find { it.item.id == item.id && it.picks == entry.picks }
            if (existing != null) existing.count = minOf(MAX_QUANTITY, existing.count + quantity) else cart.add(entry)
            navigate("Cart")
        }
        if (!item.inStock) { add.background = bg(muted); add.setOnClickListener(null) }
        bottom.addView(add, LinearLayout.LayoutParams(dp(166), dp(56))); root.addView(bottom)
    }

    // ------------------------------------------------------------------ cart and checkout

    private fun total() = cart.sumOf { it.unitPrice * it.count }

    private fun cartScreen(checkout: Boolean) {
        header(if (checkout) "Order Summary" else "My Cart", if (checkout || cart.isEmpty()) null else icon("trash", red) {
            AlertDialog.Builder(this).setTitle("Clear your cart?").setPositiveButton("Clear") { _, _ -> cart.clear(); show("Cart") }.setNegativeButton("Cancel", null).show()
        })
        val b = content(); var table: EditText? = null; var time: TextView? = null
        if (checkout) {
            if (qr == null) load({ api.qrPayment() }, failed = {}) { qr = it; if (it.enabled) redraw() }
            chips(b, listOf("Dine in", "Pickup"), { (it == "Pickup") == pickup }) { pickup = it == "Pickup"; show("Checkout") }; space(b, 28)
            if (pickup) {
                time = text(pickupTime?.let { "Pickup time: $it" } ?: "Choose pickup time", 14f, if (pickupTime == null) muted else ink).apply {
                    gravity = Gravity.CENTER_VERTICAL; background = bg(Color.WHITE, 12, line); setPadding(dp(16), 0, dp(16), 0)
                    setOnClickListener { choosePickupTime() }
                }
                b.addView(time, LinearLayout.LayoutParams(-1, dp(52)))
            } else {
                table = field("Table Number", tableNumber).apply { filters = arrayOf(InputFilter.LengthFilter(20)); onChange { tableNumber = it } }
                b.addView(table)
            }
            divider(b)
        } else { b.addView(text("You have ${count(cart.sumOf { it.count })} in your cart", 15f, ink, true)); space(b, 24) }
        val heading = row(); heading.addView(text("Order Items", 17f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(text("+ Add More", 14f, green, true).apply { setOnClickListener { history.clear(); show("Home") } }); b.addView(heading); space(b, 20)
        if (cart.isEmpty()) { b.addView(text("Your cart is empty", 20f, ink, true)); space(b, 10); b.addView(text("Find your favorite cup on the menu.", 14f, muted)) }
        cart.toList().forEach { item ->
            val r = row(); r.addView(photo(item.item.imageUrl, 54), LinearLayout.LayoutParams(dp(54), dp(54)).apply { rightMargin = dp(12) })
            val label = column(); label.addView(text(item.item.name, 14f, ink, true)); space(label, 4)
            label.addView(text(listOf(item.label, money(item.unitPrice)).filter { it.isNotEmpty() }.joinToString(" • "), 12f, muted))
            r.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(text("−", 20f, muted).apply { setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { item.count--; if (item.count == 0) cart.remove(item); show(screen) } })
            r.addView(text("${item.count}", 14f))
            r.addView(text("+", 20f).apply { setPadding(dp(10), dp(10), dp(4), dp(10)); setOnClickListener { if (item.count < MAX_QUANTITY) item.count++; show(screen) } })
            b.addView(r); space(b, 20)
        }
        divider(b)
        if (checkout) { b.addView(text("Payment Summary", 17f, ink, true)); space(b, 18); summary(b, "Subtotal", money(total())); divider(b) }
        summary(b, "Total", money(total()))
        if (cart.isNotEmpty()) {
            if (checkout) {
                val qrOn = qr?.enabled == true
                if (!qrOn) payment = "cash"
                space(b, 24); b.addView(text("Payment method", 17f, ink, true)); space(b, 12)
                chips(b, if (qrOn) listOf("Cash", "QR Payment") else listOf("Cash"), { (it == "Cash") == (payment == "cash") }) {
                    payment = if (it == "Cash") "cash" else "manual_qr"; show("Checkout")
                }
                space(b, 8)
                b.addView(text(if (payment == "cash") "Pay at the counter when you collect your order." else "You'll see the shop's QR code after placing the order.", 13f, muted))
            }
            footer(if (checkout) "Place Order" else "Checkout Now") {
                when {
                    !checkout -> { qr = null; navigate("Checkout") }
                    !pickup && tableNumber.isBlank() -> { table?.error = "Table number is required"; table?.requestFocus() }
                    pickup && pickupTime == null -> { toast("Choose a pickup time"); time?.background = bg(Color.WHITE, 12, red) }
                    else -> placeOrder()
                }
            }
        }
        if (!checkout) nav("Home")
    }

    private fun choosePickupTime() {
        val now = Calendar.getInstance().apply { add(Calendar.MINUTE, 15) }
        TimePickerDialog(this, { _, hour, minute ->
            pickupTime = String.format(Locale.US, "%02d:%02d", hour, minute); show("Checkout")
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
    }

    private fun placeOrder() {
        val lines = cart.map { NewOrderLine(it.item.id, it.count, it.picks) }
        val type = if (pickup) "pickup" else "dine_in"
        menuLoadedAt = 0 // stock changes either way: on success it drops, on failure ours was out of date
        submit({ api.placeOrder(type, tableNumber.trim(), pickupTime, payment, lines) }) { order ->
            cart.clear(); tableNumber = ""; pickupTime = null
            currentOrder = order; ordersByTab.clear(); orderTab = "active"
            history.clear()
            show(if (order.paymentMethod == "manual_qr") "Pay" else "Success")
        }
    }

    private fun success() {
        val b = content(); space(b, 100); b.addView(text("✓", 80f, green, true).apply { gravity = Gravity.CENTER }); space(b, 28)
        b.addView(text("Order Successful!", 27f, ink, true).apply { gravity = Gravity.CENTER }); space(b, 16)
        val number = currentOrder?.let { "Order #${it.number} has been placed." } ?: "Your order has been placed."
        b.addView(text("$number You can follow its progress in My Orders.", 16f, muted).apply { gravity = Gravity.CENTER })
        footer("View My Orders") { history.clear(); show("Orders") }
    }

    // ------------------------------------------------------------------ orders

    private fun statusColor(order: Order) = when (order.status) {
        "cancelled", "rejected" -> red
        "completed" -> ink
        else -> green
    }

    private fun ordersScreen() {
        header("My Orders"); val b = content()
        chips(b, orderTabs.map { it.first }, { name -> orderTabs.first { it.first == name }.second == orderTab }) { name ->
            orderTab = orderTabs.first { it.first == name }.second; show("Orders")
        }
        space(b, 20)
        val tab = orderTab
        val reload = {
            ordersError = null
            load({ api.orders(tab) }, failed = { ordersError = it.message; redraw() }) {
                if (ordersByTab[tab] != it) { ordersByTab[tab] = it; redraw() }
            }
        }
        if (!quiet) reload()
        every(ORDER_POLL_MS) { if (ordersError == null) reload() }
        val list = ordersByTab[tab]
        val label = orderTabs.first { it.second == tab }.first.lowercase()
        when {
            list == null && ordersError != null -> notice(b, "Couldn't load your orders", ordersError!!) { ordersError = null; show("Orders") }
            list == null -> notice(b, "Loading orders…", "One moment.")
            list.isEmpty() -> notice(b, "No $label orders", "Your orders will appear here after checkout.")
            else -> list.forEach { o ->
                val card = column().apply {
                    background = bg(Color.WHITE, 12, line); setPadding(dp(16), dp(16), dp(16), dp(16))
                    setOnClickListener { currentOrder = o; navigate("Order") }
                }
                summary(card, "Order #${o.number}", o.statusLabel, statusColor(o)); space(card, 12)
                card.addView(text(o.place, 13f, Color.GRAY)); divider(card)
                summary(card, count(o.itemCount), money(o.total)); b.addView(card); space(b, 12)
            }
        }
        nav("Orders")
    }

    /** Order details (design: Transaction Details), refreshed every few seconds to track its status. */
    private fun orderScreen() {
        val o = currentOrder ?: return show("Orders")
        val refresh = { load({ api.order(o.id) }, failed = {}) { if (it != currentOrder) { currentOrder = it; redraw() } } }
        if (!quiet) refresh()
        every(ORDER_POLL_MS) { refresh() }

        header("Transaction Details"); val b = content()
        b.addView(text("Order #${o.number}", 24f, ink, true)); space(b, 6)
        b.addView(text(o.createdAt.replace("T", " ").take(16), 13f, muted)); space(b, 24)
        summary(b, "Status", o.statusLabel, statusColor(o))
        if (o.rejectionReason.isNotBlank()) { space(b, 8); b.addView(text("Reason: ${o.rejectionReason}", 14f, red)) }
        divider(b); summary(b, "Order type", o.place)
        divider(b); summary(b, "Payment", o.paymentMethodLabel)
        divider(b); summary(b, "Payment status", o.paymentStatusLabel, if (o.paymentStatus == "paid") green else ink)
        divider(b); b.addView(text("Items", 17f, ink, true)); space(b, 16)
        o.items.forEach { item ->
            val r = row().apply { gravity = Gravity.TOP }
            r.addView(photo(item.imageUrl, 44), LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(12) })
            val label = column(); label.addView(text("${item.quantity} × ${item.name}", 14f, ink, true))
            if (item.options.isNotBlank()) { space(label, 4); label.addView(text(item.options, 12f, muted)) }
            r.addView(label, LinearLayout.LayoutParams(0, -2, 1f)); r.addView(text(money(item.lineTotal), 14f, ink, true))
            b.addView(r); space(b, 16)
        }
        divider(b); summary(b, "Total", money(o.total))
        if (o.canUploadProof) {
            space(b, 24)
            b.addView(button(if (o.paymentProofUrl == null) "Pay by QR" else "View QR / replace screenshot") { navigate("Pay") })
        }
        if (o.canCancel) {
            space(b, 12)
            b.addView(outline("Cancel Order", red) {
                AlertDialog.Builder(this).setTitle("Cancel this order?").setMessage("Order #${o.number} will be cancelled.")
                    .setPositiveButton("Cancel Order") { _, _ ->
                        submit({ api.cancelOrder(o.id) }) { currentOrder = it; ordersByTab.clear(); menuLoadedAt = 0; redraw() }
                    }.setNegativeButton("Keep", null).show()
            })
        }
    }

    /** Manual QR payment: show the shop's QR and let the customer send a screenshot of the transfer. */
    private fun payScreen() {
        val o = currentOrder ?: return show("Orders")
        if (!quiet) {
            load({ api.qrPayment() }, failed = {}) { if (it != qr) { qr = it; redraw() } }
            load({ api.order(o.id) }, failed = {}) { if (it != currentOrder) { currentOrder = it; redraw() } }
        }
        every(ORDER_POLL_MS) { load({ api.order(o.id) }, failed = {}) { if (it != currentOrder) { currentOrder = it; redraw() } } }

        header("QR Payment"); val b = content()
        b.addView(text("Order #${o.number}", 20f, ink, true).apply { gravity = Gravity.CENTER }); space(b, 6)
        b.addView(text("Amount to pay: ${money(o.total)}", 16f, green, true).apply { gravity = Gravity.CENTER }); space(b, 20)
        val shop = qr
        when {
            shop == null -> b.addView(text("Loading the shop's QR code…", 14f, muted).apply { gravity = Gravity.CENTER })
            shop.qrImageUrl == null -> b.addView(text("The shop's QR code isn't available right now. Please pay at the counter.", 14f, muted).apply { gravity = Gravity.CENTER })
            else -> {
                b.addView(photo(shop.qrImageUrl, 260).apply { scaleType = ImageView.ScaleType.FIT_CENTER; background = bg(Color.WHITE, 12, line) }); space(b, 16)
                if (shop.accountName.isNotBlank()) summary(b, "Account name", shop.accountName, ink)
                if (shop.khrAccount.isNotBlank()) { space(b, 8); summary(b, "KHR account", shop.khrAccount, ink) }
                if (shop.usdAccount.isNotBlank()) { space(b, 8); summary(b, "USD account", shop.usdAccount, ink) }
                if (shop.instructions.isNotBlank()) { space(b, 16); b.addView(text(shop.instructions, 14f, Color.GRAY)) }
            }
        }
        divider(b)
        val state = when {
            o.paymentStatus == "paid" -> "Payment confirmed ✓"
            !o.canUploadProof -> "This order is no longer waiting for a payment."
            o.paymentProofUrl != null -> "Screenshot sent. Staff will confirm your payment shortly."
            else -> "After paying, upload a screenshot of the transfer so staff can confirm it."
        }
        b.addView(text(state, 14f, if (o.paymentStatus == "paid") green else ink, o.paymentStatus == "paid")); space(b, 16)
        if (o.canUploadProof) {
            b.addView(outline(if (o.paymentProofUrl == null) "Upload payment screenshot" else "Replace screenshot", green) {
                try {
                    startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), PICK_PROOF)
                } catch (e: Exception) { toast("No app available to choose a picture.") }
            })
        }
        footer("View My Orders") { history.clear(); orderTab = "active"; show("Orders") }
    }

    @Deprecated("Result of the picture chooser")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val picture: Uri = data?.data ?: return
        val order = currentOrder ?: return
        if (requestCode != PICK_PROOF || resultCode != RESULT_OK) return
        submit({
            // Shrink to a JPEG well under the server's 2 MB limit before sending.
            val bytes = contentResolver.openInputStream(picture)?.use { it.readBytes() }
            val bitmap = bytes?.let { Images.decode(it, 1600) } ?: throw ApiException(0, "That file couldn't be read as a picture.")
            val jpeg = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
            api.uploadPaymentProof(order.id, jpeg)
        }) {
            currentOrder = it; ordersByTab.clear()
            toast("Screenshot uploaded")
            if (screen == "Pay" || screen == "Order") redraw()
        }
    }

    // ------------------------------------------------------------------ profile

    private fun displayName() = profile?.fullName?.takeIf { it.isNotBlank() } ?: "Moni Coffee Guest"
    private fun initials() = displayName().split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")

    private fun profileScreen() {
        if (!quiet) load({ api.me() }, failed = {}) { if (it != profile) { saveProfile(it); redraw() } }
        val b = content(); space(b, 42); val identity = row()
        identity.addView(text(initials(), 32f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = bg(Color.rgb(22, 100, 74), 100) }, LinearLayout.LayoutParams(dp(76), dp(76)).apply { rightMargin = dp(20) })
        identity.addView(column().apply { addView(text(displayName(), 25f, ink, true)); space(this, 8); addView(text(profile?.phone ?: "", 15f, muted)) })
        b.addView(identity); space(b, 40); b.addView(text("Basic Info", 22f, ink, true)); space(b, 12)
        listOf("Order History" to "Orders", "Personal Info" to "Personal Info", "Payment Method" to "Payment Method", "My Favorites" to "Favorites").forEach { (label, target) ->
            b.addView(text("$label    ›", 18f).apply { setPadding(0, dp(12), 0, dp(12)); setOnClickListener { navigate(target) } }); divider(b)
        }
        summary(b, "App Version", "1.0")
        footer("Log Out") {
            AlertDialog.Builder(this).setTitle("Log out?").setMessage("You can sign in again with your phone number.")
                .setPositiveButton("Log Out") { _, _ ->
                    // Sign out on this phone even if the server can't be reached to drop the token.
                    submit({ try { api.logout() } catch (e: ApiException) { } }) { signOut() }
                }.setNegativeButton("Cancel", null).show()
        }; nav("Profile")
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
        header("Payment method"); val b = content(); b.addView(text("Payment Method", 24f, ink, true)); space(b, 30)
        val qrOn = qr?.enabled == true
        listOf("cash" to "Cash", "manual_qr" to "Manual QR Payment").forEach { (value, label) ->
            val usable = value == "cash" || qrOn
            val chosen = value == payment && usable
            b.addView(text("${if (value == "cash") "▣" else "QR"}     $label   ${if (chosen) "✓" else ""}", 21f, if (chosen) green else if (usable) ink else muted).apply {
                setPadding(0, dp(18), 0, dp(18))
                if (usable) setOnClickListener { payment = value; prefs.edit().putString("payment", value).apply(); show("Payment Method") }
            })
            if (!usable) b.addView(text("Not offered by the shop at the moment.", 13f, muted))
            divider(b)
        }
    }

    // ------------------------------------------------------------------ login and registration

    private fun auth(mode: String) {
        val verifying = mode == "Verification"
        val b = content(); space(b, 70)
        b.addView(text(if (verifying) "OTP Verification" else "Moni Coffee", 32f, ink, true).apply { gravity = Gravity.CENTER }); space(b, 12)
        b.addView(text(if (verifying) "Enter the $CODE_LENGTH-digit code sent to $authPhone." else "Enter your information below.", 14f, muted).apply { gravity = Gravity.CENTER }); space(b, 52)
        val name = field("Full name", authName); if (mode == "Register") b.addView(name)
        val number = field(if (verifying) "$CODE_LENGTH-digit code" else "Phone number", if (verifying) "" else authPhone, numeric = true)
        if (verifying) { number.inputType = InputType.TYPE_CLASS_NUMBER; number.filters = arrayOf(InputFilter.LengthFilter(CODE_LENGTH)) }
        b.addView(number)
        if (verifying) {
            // The backend only includes the code in its reply while it runs in DEBUG mode (no SMS gateway yet).
            ticket?.debugCode?.let { b.addView(text("Demo server: your code is $it", 13f, green, true)); space(b, 12) }
            b.addView(text("Resend code", 14f, green, true).apply {
                setOnClickListener { requestCode(authPurpose, authPhone, authName) { toast("A new code was sent."); redraw() } }
            })
            // The shop's SMS device can confirm the code for us; pick that up without typing.
            ticket?.let { t -> every(OTP_POLL_MS) { load({ api.otpStatus(authPurpose, t.requestId) }, failed = {}) { it?.let(::signIn) } } }
        }
        space(b, 40)
        if (mode == "Register") { b.addView(text("By signing up, you agree to our Terms and Conditions and our Privacy Policy.", 14f, muted).apply { gravity = Gravity.CENTER }); space(b, 16) }
        b.addView(button(if (mode == "Login") "Log in" else if (mode == "Register") "Sign up" else "Verify") {
            val typed = number.text.toString().trim()
            when {
                mode == "Register" && name.text.isBlank() -> name.error = "Enter your name"
                typed.isEmpty() -> number.error = "Required"
                verifying && typed.length != CODE_LENGTH -> number.error = "Enter $CODE_LENGTH digits"
                verifying -> submit({ api.verifyOtp(authPurpose, authPhone, typed) }) { signIn(it) }
                else -> requestCode(if (mode == "Login") "login" else "register", typed, name.text.toString().trim()) { navigate("Verification") }
            }
        }); space(b, 24)
        if (!verifying) {
            b.addView(text("Server: ${api.baseUrl}  (change)", 12f, muted).apply { gravity = Gravity.CENTER; setOnClickListener { chooseServer() } })
        }
        footer(if (mode == "Login") "Don’t have an account? Sign up" else "Already have an account? Log in") {
            history.clear(); show(if (mode == "Login") "Register" else "Login")
        }
    }

    private fun requestCode(purpose: String, phone: String, fullName: String, then: () -> Unit) {
        submit({ api.requestOtp(purpose, phone, fullName.takeIf { purpose == "register" }) }) {
            authPurpose = purpose; authPhone = phone; authName = fullName; ticket = it
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
                "orders", "cart" -> { path(11f, 16f, 29f, 16f, 31f, 32f, 9f, 32f, 11f, 16f); c.drawArc(16f, 8f, 24f, 23f, 180f, 180f, false, p) }
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
    }
}
