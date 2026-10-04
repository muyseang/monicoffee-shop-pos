package com.monicoffee.pos

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.Gravity
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import java.util.Locale

/** Native, offline UI prototype based on the supplied Moni Coffee design screens. */
class MainActivity : Activity() {
    private val green = Color.rgb(0, 125, 87)
    private val ink = Color.rgb(44, 49, 48)
    private val muted = Color.rgb(160, 160, 160)
    private val line = Color.rgb(232, 232, 232)
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private var screen = "Home"
    private var category = "Coffee"
    private var query = ""
    private var size = "L"
    private var quantity = 1
    private val addons = mutableSetOf<String>()
    private var pickup = false
    private var payment = "Cash"
    private var orderTab = "In Progress"
    private var customer = "Sokha Chan"
    private var phone = "+855 *** 150 874"
    private var address = "#12, Street 289, Phnom Penh"
    private val favorites = mutableSetOf<Int>()
    private val cart = mutableListOf<CartItem>()
    private val orders = mutableListOf<Order>()
    private val history = java.util.ArrayDeque<String>()
    private data class Product(val name: String, val size: String, val price: Double, val photo: Int, val category: String = "Coffee")
    private data class CartItem(val product: Int, val size: String, val extras: Set<String>, var count: Int, val price: Double)
    private data class Order(val id: String, val type: String, val total: Double, val count: Int, val payment: String)
    private val products = listOf(
        Product("Hazelnut Twist", "Small", 10.50, R.drawable.coffee_hazelnut),
        Product("Mocha Swirl", "Medium", 9.99, R.drawable.coffee_mocha),
        Product("Cinnamon Spice", "Large", 11.00, R.drawable.coffee_cinnamon),
        Product("Honey Almond", "Extra Large", 10.75, R.drawable.coffee_honey),
        Product("Green Tea Latte", "Medium", 5.50, R.drawable.coffee_cinnamon, "Tea"),
        Product("Iced Mocha", "Large", 7.00, R.drawable.coffee_mocha, "Cold Drinks")
    )
    private var selected = 1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getPreferences(MODE_PRIVATE)
        customer = prefs.getString("name", customer)!!
        phone = prefs.getString("phone", phone)!!
        address = prefs.getString("address", address)!!
        show("Home")
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun money(n: Double) = String.format(Locale.US, "$%.2f", n)
    private fun bg(color: Int = Color.WHITE, radius: Int = 12, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); stroke?.let { setStroke(dp(1), it) }
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun text(value: String, sp: Float = 16f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = sp; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun space(parent: LinearLayout, height: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun divider(parent: LinearLayout) { parent.addView(View(this).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(16); bottomMargin = dp(16) }) }
    private fun button(label: String, action: () -> Unit): TextView = text(label, 16f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER; background = bg(green); minHeight = dp(56); setPadding(dp(if (label.length == 1) 0 else 16), dp(if (label.length == 1) 0 else 12), dp(if (label.length == 1) 0 else 16), dp(if (label.length == 1) 0 else 12)); setOnClickListener { action() }
    }
    private fun field(hintText: String, value: String = "", numeric: Boolean = false) = EditText(this).apply {
        hint = hintText; setText(value); textSize = 14f; setTextColor(ink); setHintTextColor(muted)
        setSingleLine(); background = bg(Color.WHITE, 12, line); setPadding(dp(16), dp(12), dp(16), dp(12))
        inputType = if (numeric) android.text.InputType.TYPE_CLASS_PHONE else android.text.InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(16) }
    }
    private fun icon(name: String, color: Int = green, action: () -> Unit): View = Icon(this, name, color).apply {
        contentDescription = name; setOnClickListener { action() }; layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
    }
    private fun image(res: Int, height: Int): ImageView = ImageView(this).apply {
        setImageResource(res); scaleType = ImageView.ScaleType.CENTER_CROP; background = bg(); clipToOutline = true
        layoutParams = LinearLayout.LayoutParams(-1, dp(height))
    }
    private fun navigate(to: String) { history.addLast(screen); show(to) }
    @Deprecated("Native back navigation") override fun onBackPressed() { if (history.isNotEmpty()) show(history.removeLast()) else if (screen != "Home") show("Home") else super.onBackPressed() }
    private fun show(to: String) {
        screen = to
        window.statusBarColor = if (to == "Home") Color.rgb(31,31,31) else Color.WHITE
        window.navigationBarColor = if (android.os.Build.VERSION.SDK_INT >= 26) Color.WHITE else ink
        window.decorView.systemUiVisibility = (if (android.os.Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0) or (if (to == "Home") 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        root = column().apply { setBackgroundColor(Color.WHITE) }; setContentView(root)
        when (to) {
            "Home", "Explore", "Favorites" -> menu(to)
            "Details" -> details()
            "Cart", "Checkout" -> cartScreen(to == "Checkout")
            "Orders" -> ordersScreen()
            "Success" -> success()
            "Transaction" -> transaction()
            else -> menu("Home")
        }
    }
    private fun header(title: String, right: View? = null) {
        val h = row().apply { setPadding(dp(16), dp(12), dp(16), dp(12)) }
        h.addView(icon("back", ink) { onBackPressed() })
        h.addView(text(title, 17f, ink, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, dp(40), 1f))
        h.addView(right ?: View(this), LinearLayout.LayoutParams(dp(40), dp(40))); root.addView(h)
    }
    private fun content(): LinearLayout {
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        body = column().apply { setPadding(dp(24), dp(12), dp(24), dp(24)) }
        scroll.addView(body); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); return body
    }
    private fun footer(label: String, action: () -> Unit) {
        root.addView(column().apply { setPadding(dp(24), dp(16), dp(24), dp(20)); elevation = dp(8).toFloat(); setBackgroundColor(Color.WHITE); addView(button(label, action)) })
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
    private fun chips(parent: LinearLayout, values: List<String>, current: String, action: (String) -> Unit) {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val r = row()
        values.forEach { value -> r.addView(text(value, 14f, if (value in current.split("|")) Color.WHITE else muted, value in current.split("|")).apply {
            gravity = Gravity.CENTER; setPadding(dp(14), dp(9), dp(14), dp(9)); background = bg(if (value in current.split("|")) green else Color.rgb(247,247,247), 8)
            setOnClickListener { action(value) }
        }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) }) }
        scroll.addView(r); parent.addView(scroll)
    }
    private fun menu(mode: String) {
        if (mode == "Home") {
            val h = column().apply { setPadding(dp(24), dp(20), dp(24), dp(14)); setBackgroundColor(Color.rgb(31,31,31)) }
            val loc = row(); val label = column(); label.addView(text("Location", 13f, muted)); space(label, 6)
            label.addView(text("Moni Coffee, Phnom Penh⌄", 15f, Color.WHITE, true)); label.setOnClickListener { navigate("Cafe") }
            loc.addView(label, LinearLayout.LayoutParams(0,-2,1f)); loc.addView(icon("cart", Color.WHITE) { navigate("Cart") }); h.addView(loc); space(h, 14)
            val searchRow = row(); val search = field("Search coffee").apply { setText(query); background = bg(Color.rgb(43,43,43)); setTextColor(Color.WHITE) }
            searchRow.addView(search, LinearLayout.LayoutParams(0,dp(48),1f)); searchRow.addView(icon("filter", Color.WHITE) {
                AlertDialog.Builder(this).setTitle("Category").setItems(arrayOf("Coffee","Tea","Pastry","Cold Drinks")) { _, i -> category = listOf("Coffee","Tea","Pastry","Cold Drinks")[i]; show("Home") }.show()
            }); h.addView(searchRow); root.addView(h)
            val b = content(); chips(b, listOf("Coffee","Tea","Pastry","Cold Drinks"), category) { category = it; show("Home") }; space(b,16)
            val grid = column(); b.addView(grid)
            fillGrid(grid, products.indices.filter { products[it].category == category && products[it].name.contains(query,true) })
            search.addTextChangedListener(object: TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); fillGrid(grid,products.indices.filter { products[it].category == category && products[it].name.contains(query,true) }) }
                override fun afterTextChanged(s: Editable?) {}
            }); nav("Home")
        } else {
            header(if (mode == "Favorites") "My Favorites" else "Explore", icon("heart") { navigate("Favorites") })
            val b = content(); val search = field("Search coffee"); b.addView(search)
            val grid = column(); b.addView(grid)
            val available = if (mode == "Favorites") favorites.toList() else products.indices.toList()
            fillGrid(grid,available)
            search.addTextChangedListener(object: TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { fillGrid(grid,available.filter { products[it].name.contains(s.toString(),true) }) }
                override fun afterTextChanged(s: Editable?) {}
            }); nav("Explore")
        }
    }
    private fun fillGrid(grid: LinearLayout, ids: List<Int>) {
        grid.removeAllViews()
        if (ids.isEmpty()) { space(grid,60); grid.addView(text(if (screen == "Favorites") "Your favorites will appear here." else "No items found in this category.",15f,muted)); return }
        ids.chunked(2).forEach { pair ->
            val r = row().apply { gravity = Gravity.TOP }
            pair.forEachIndexed { index, id ->
                val p = products[id]; val card = column().apply { setPadding(dp(8),dp(8),dp(8),dp(12)); background = bg(); elevation = dp(3).toFloat() }
                card.addView(image(p.photo,128)); space(card,8); card.addView(text(p.name,16f,ink,true)); space(card,4); card.addView(text(p.size,11f,muted)); space(card,6)
                val price = row(); price.addView(text(money(p.price),19f,Color.BLACK,true),LinearLayout.LayoutParams(0,-2,1f)); price.addView(button("+") { selected=id; quantity=1; size="L"; addons.clear(); navigate("Details") }.apply { minHeight=dp(32) },LinearLayout.LayoutParams(dp(32),dp(32))); card.addView(price)
                card.setOnClickListener { selected=id; quantity=1; size="L"; addons.clear(); navigate("Details") }
                r.addView(card,LinearLayout.LayoutParams(0,-2,1f).apply { if(index==0) rightMargin=dp(16) })
            }
            if(pair.size==1) r.addView(View(this),LinearLayout.LayoutParams(0,1,1f)); grid.addView(r); space(grid,16)
        }
    }
    private fun unitPrice() = products[selected].price + (if(size=="L") 2.0 else if(size=="M") 1.0 else 0.0) + addons.size * 0.5
    private fun details() {
        val p=products[selected]
        header("Details", icon("heart", if(selected in favorites) Color.RED else muted) { if(!favorites.add(selected)) favorites.remove(selected); show("Details") })
        val b=content(); b.addView(image(if (selected == 1) R.drawable.coffee_detail else p.photo,200)); space(b,24)
        val title=row(); title.addView(text(p.name,19f,ink,true),LinearLayout.LayoutParams(0,-2,1f)); title.addView(text("♧  Coffee",13f,green)); b.addView(title)
        space(b,6); b.addView(text("Iced/Hot",13f,muted)); divider(b); b.addView(text("Description",15f,ink,true)); space(b,14)
        b.addView(text("${p.name}, a unique blend of rich coffee, silky milk, and sweet caramel, delivering a layered taste experience. Every sip is a sweet tribute to the good life.",14f,Color.GRAY).apply { setLineSpacing(dp(4).toFloat(),1f) }); space(b,24)
        b.addView(text("Add-ons",15f,ink,true)); space(b,12)
        chips(b,listOf("Extra Shot","Vanilla Syrup","Caramel Syrup"),addons.joinToString("|")) { if(!addons.add(it)) addons.remove(it); show("Details") }
        space(b,24)
        val options=row().apply { gravity=Gravity.TOP }
        val sizes=column(); sizes.addView(text("Size",15f,ink,true)); space(sizes,12)
        chips(sizes,listOf("L","M","S"),size) { size=it; show("Details") }
        options.addView(sizes,LinearLayout.LayoutParams(0,-2,1f))
        val quantities=column(); quantities.addView(text("Quantity",15f,ink,true)); space(quantities,12)
        val q=row()
        q.addView(button("−") { if(quantity>1) quantity--; show("Details") }.apply { minHeight=dp(36) },LinearLayout.LayoutParams(dp(36),dp(36)))
        q.addView(text("  $quantity  ",17f)); q.addView(button("+") { quantity++; show("Details") }.apply { minHeight=dp(36) },LinearLayout.LayoutParams(dp(36),dp(36)))
        quantities.addView(q); options.addView(quantities); b.addView(options)
        val bottom=row().apply { setPadding(dp(24),dp(16),dp(24),dp(20)); elevation=dp(8).toFloat(); setBackgroundColor(Color.WHITE) }
        val price=column(); price.addView(text(money(unitPrice()*quantity),25f,ink,true)); price.addView(text("Total Price",13f,muted))
        bottom.addView(price,LinearLayout.LayoutParams(0,-2,1f))
        bottom.addView(button("Add to Cart") {
            val existing=cart.find { it.product==selected && it.size==size && it.extras==addons }
            if(existing!=null) existing.count+=quantity else cart.add(CartItem(selected,size,addons.toSet(),quantity,unitPrice()))
            navigate("Cart")
        },LinearLayout.LayoutParams(dp(166),dp(56))); root.addView(bottom)
    }
    private fun total() = cart.sumOf { it.price*it.count }
    private fun cartScreen(checkout: Boolean) {
        header(if(checkout) "Order Summary" else "My Cart", if(checkout) null else icon("trash",Color.rgb(190,46,31)) { AlertDialog.Builder(this).setTitle("Clear your cart?").setPositiveButton("Clear") { _,_->cart.clear(); show("Cart") }.setNegativeButton("Cancel",null).show() })
        val b=content(); var table: EditText?=null
        if(checkout) {
            chips(b,listOf("Dine in","Pickup"),if(pickup) "Pickup" else "Dine in") { pickup=it=="Pickup"; show("Checkout") }; space(b,28)
            table=field(if(pickup) "Pickup time (e.g. 11:00 AM)" else "Table Number",numeric=!pickup); b.addView(table); divider(b)
        } else { b.addView(text("You have ${cart.sumOf { it.count }} items in your cart",15f,ink,true)); space(b,24) }
        val heading=row(); heading.addView(text("Order Items",17f,ink,true),LinearLayout.LayoutParams(0,-2,1f)); heading.addView(text("+ Add More",14f,green,true).apply { setOnClickListener { show("Home") } }); b.addView(heading); space(b,20)
        if(cart.isEmpty()) { b.addView(text("Your cart is empty",20f,ink,true)); space(b,10); b.addView(text("Find your favorite cup on the menu.",14f,muted)) }
        cart.toList().forEach { item ->
            val r=row(); r.addView(image(products[item.product].photo,54),LinearLayout.LayoutParams(dp(54),dp(54)).apply { rightMargin=dp(12) })
            val label=column(); label.addView(text(products[item.product].name,14f,ink,true)); space(label,4); label.addView(text("${item.size} • ${money(item.price)}",12f,muted)); r.addView(label,LinearLayout.LayoutParams(0,-2,1f))
            r.addView(text("−",20f,muted).apply { setPadding(dp(10),dp(10),dp(10),dp(10)); setOnClickListener { item.count--; if(item.count==0) cart.remove(item); show(screen) } }); r.addView(text("${item.count}",14f)); r.addView(text("+",20f).apply { setPadding(dp(10),dp(10),dp(4),dp(10)); setOnClickListener { item.count++; show(screen) } }); b.addView(r); space(b,20)
        }
        divider(b); if(checkout) { b.addView(text("Payment Summary",17f,ink,true)); space(b,18); summary(b,"Subtotal",money(total())); divider(b) }; summary(b,"Total",money(total()))
        if(cart.isNotEmpty()) {
            if(checkout) { space(b,24); b.addView(text("$payment  ⌄",16f,green,true).apply { setOnClickListener { AlertDialog.Builder(this@MainActivity).setTitle("Payment method").setItems(arrayOf("Cash","Manual QR Payment")) { _,i->payment=if(i==0) "Cash" else "Manual QR Payment"; show("Checkout") }.show() } }) }
            footer(if(checkout) "Place Order" else "Checkout Now") {
                if(!checkout) navigate("Checkout") else {
                    val value=table?.text.toString().trim()
                    if(value.isEmpty()) { table?.error=if(pickup) "Pickup time is required" else "Table number is required"; table?.requestFocus() }
                    else { orders.add(0,Order("MC-${145+orders.size}",if(pickup) "Pickup • $value" else "Dine-in • Table $value",total(),cart.sumOf { it.count },payment)); cart.clear(); navigate("Success") }
                }
            }
        }; if(!checkout) nav("Home")
    }
    private fun summary(parent: LinearLayout,label: String,value: String) { parent.addView(row().apply { addView(text(label,16f),LinearLayout.LayoutParams(0,-2,1f)); addView(text(value,16f,green,true)) }) }
    private fun ordersScreen() {
        header("My Orders"); val b=content(); chips(b,listOf("In Progress","Completed","Cancelled"),orderTab) { orderTab=it; show("Orders") }; space(b,20)
        if(orderTab=="In Progress" && orders.isNotEmpty()) orders.forEach { o ->
            val card=column().apply { background=bg(Color.WHITE,12,line); setPadding(dp(16),dp(16),dp(16),dp(16)); setOnClickListener { navigate("Transaction") } }
            summary(card,"Order #${o.id}","Pending"); space(card,12); card.addView(text(o.type,13f,Color.GRAY)); divider(card); summary(card,"${o.count} items",money(o.total)); b.addView(card); space(b,12)
        } else { space(b,60); b.addView(text("No ${orderTab.lowercase()} orders",20f,ink,true)); space(b,10); b.addView(text("Your orders will appear here after checkout.",14f,muted)) }; nav("Orders")
    }
    private fun success() {
        val b=content(); space(b,100); b.addView(text("✓",80f,green,true).apply { gravity=Gravity.CENTER }); space(b,28); b.addView(text("Order Successful!",27f,ink,true).apply { gravity=Gravity.CENTER }); space(b,16); b.addView(text("Your order has been placed. You can follow its progress in My Orders.",16f,muted).apply { gravity=Gravity.CENTER }); footer("View My Orders") { history.clear(); show("Orders") }
    }
    private fun transaction() {
        header("Transaction Details"); val b=content(); orders.firstOrNull()?.let { o-> b.addView(text("Order #${o.id}",24f,ink,true)); space(b,24); summary(b,"Status","Pending"); divider(b); summary(b,"Order type",o.type); divider(b); summary(b,"Payment",o.payment); divider(b); summary(b,"Items","${o.count}"); divider(b); summary(b,"Total",money(o.total)) }
    }
    private class Icon(context: android.content.Context, val kind: String, val tint: Int): View(context) {
        override fun onDraw(c: Canvas) {
            super.onDraw(c); val s=width/40f; c.save(); c.scale(s,s)
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=tint; strokeWidth=2f; style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
            fun path(vararg points: Float) { val a=Path(); a.moveTo(points[0],points[1]); for(i in 2 until points.size step 2) a.lineTo(points[i],points[i+1]); c.drawPath(a,p) }
            when(kind) {
                "home" -> { path(10f,19f,20f,10f,30f,19f,30f,31f,23f,31f,23f,23f,17f,23f,17f,31f,10f,31f,10f,19f) }
                "explore" -> { c.drawCircle(18f,17f,8f,p); path(24f,23f,32f,31f) }
                "profile" -> { c.drawCircle(20f,20f,11f,p); c.drawCircle(20f,16f,4f,p); c.drawArc(12f,21f,28f,35f,195f,150f,false,p) }
                "orders", "cart" -> { path(11f,16f,29f,16f,31f,32f,9f,32f,11f,16f); c.drawArc(16f,8f,24f,23f,180f,180f,false,p) }
                "back" -> { path(23f,12f,15f,20f,23f,28f); path(15f,20f,30f,20f) }
                "trash" -> { path(13f,14f,14f,31f,26f,31f,27f,14f); path(11f,12f,29f,12f); path(17f,9f,23f,9f) }
                "heart" -> { val a=Path(); a.moveTo(20f,31f); a.cubicTo(0f,18f,12f,5f,20f,14f); a.cubicTo(28f,5f,40f,18f,20f,31f); c.drawPath(a,p) }
                else -> { path(10f,13f,30f,13f); path(10f,26f,30f,26f); c.drawCircle(17f,13f,3f,p); c.drawCircle(25f,26f,3f,p) }
            }; c.restore()
        }
    }
}
