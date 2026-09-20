package com.monicoffee.pos

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createMenuScreen())
    }

    private fun createMenuScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(255, 248, 225))
            setPadding(dp(24), dp(28), dp(24), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "MONI COFFEE"
            textSize = 13f
            setTextColor(Color.rgb(78, 52, 46))
            letterSpacing = 0.12f
        })
        root.addView(TextView(this).apply {
            text = "Order your favourite cup"
            textSize = 28f
            setTextColor(Color.rgb(40, 26, 22))
            setPadding(0, dp(6), 0, dp(6))
        })
        root.addView(TextView(this).apply {
            text = "Week 1 foundation - menu data will connect to the Django API in the next implementation phase."
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, dp(24))
        })

        listOf(
            Triple("Espresso", "Single shot espresso", "$2.00"),
            Triple("Latte", "Espresso with steamed milk", "$3.00"),
            Triple("Green Tea", "Fresh and simple", "$2.00")
        ).forEach { (name, description, price) -> root.addView(menuItem(name, description, price)) }

        root.addView(TextView(this).apply {
            text = "Cart and checkout will be delivered after menu API integration."
            textSize = 13f
            setTextColor(Color.rgb(78, 52, 46))
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        })
        return root
    }

    private fun menuItem(name: String, description: String, price: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(16).toFloat()
        }
        val label = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        label.addView(TextView(this@MainActivity).apply { text = name; textSize = 18f; setTextColor(Color.rgb(40, 26, 22)) })
        label.addView(TextView(this@MainActivity).apply { text = description; textSize = 13f; setTextColor(Color.DKGRAY) })
        addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@MainActivity).apply { text = price; textSize = 16f; setTextColor(Color.rgb(78, 52, 46)) })
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
