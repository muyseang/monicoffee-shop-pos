package com.monicoffee.pos.ui.menu

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.R
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette
import com.monicoffee.pos.ui.widget.FlowRow

/**
 * Design 28: a search field with the categories and recent searches under it while it is empty;
 * typing (or picking a category or a recent search) shows the matching menu items instead.
 */
class ExploreScreen(app: MainActivity) : MenuScreen(app) {
    private var query = ""

    override fun draw() {
        if (!quiet) refreshMenu()
        val top = row().apply { setPadding(dp(24), dp(16), dp(24), 0) }
        top.addView(vector(R.drawable.ic_back).apply { contentDescription = "Back"; setOnClickListener { back() } }, LinearLayout.LayoutParams(dp(36), dp(36)))
        val search = field("Search coffee...", query).apply {
            background = null; setHintTextColor(Color.rgb(170, 170, 170)); letterSpacing = -0.01f; setPadding(dp(10), 0, dp(16), 0)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        top.addView(row().apply {
            background = bg(Color.rgb(244, 244, 244), 12); setPadding(dp(24), 0, 0, 0)
            addView(vector(R.drawable.ic_search_line), LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(search, LinearLayout.LayoutParams(0, dp(48), 1f))
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(16) })
        root.addView(top)

        val b = content(); space(b, 12)
        val suggestions = column(); val grid = column()
        b.addView(suggestions); b.addView(grid)
        val find = { text: String ->
            query = text.trim()
            suggestions.visibility = if (query.isEmpty()) View.VISIBLE else View.GONE
            grid.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
            if (query.isNotEmpty()) fillGrid(grid, menu.items.filter { it.name.contains(query, true) || it.categoryName.equals(query, true) })
        }
        // A category, a recent search or the keyboard's search key: search for it and remember it.
        val searchFor = { text: String ->
            val term = text.trim()
            if (term.isNotEmpty()) prefs.recentSearches = (listOf(term) + prefs.recentSearches.filterNot { it.equals(term, true) }).take(MAX_RECENT)
            search.setText(term); search.setSelection(term.length)
        }

        val pills = FlowRow(context, dp(11), dp(16))
        menu.categories.forEach { c ->
            pills.addView(text(c.name, 14f, Color.rgb(132, 132, 132)).apply {
                background = bg(Color.rgb(244, 244, 244), 12); setPadding(dp(16), dp(8), dp(16), dp(8)); letterSpacing = -0.01f; leading(1.6f)
                setOnClickListener { searchFor(c.name) }
            })
        }
        suggestions.addView(pills, LinearLayout.LayoutParams(-1, -2))
        val recent = prefs.recentSearches
        if (recent.isNotEmpty()) {
            space(suggestions, 38)
            suggestions.addView(row().apply {
                addView(text("Recent Searches", 16f, Color.rgb(36, 36, 36), true).apply { leading(1.5f) }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(text("Clear All", 14f, Palette.green, true).apply { leading(1.5f); setOnClickListener { prefs.recentSearches = emptyList(); redraw() } })
            }); space(suggestions, 24)
            recent.forEach { term ->
                suggestions.addView(row().apply {
                    addView(vector(R.drawable.ic_history), LinearLayout.LayoutParams(dp(22), dp(22)))
                    addView(text(term, 14f, Color.rgb(163, 163, 163)).apply { letterSpacing = -0.01f; maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(19) })
                    addView(vector(R.drawable.ic_close).apply {
                        contentDescription = "Remove"; setOnClickListener { prefs.recentSearches = prefs.recentSearches - term; redraw() }
                    }, LinearLayout.LayoutParams(dp(22), dp(22)))
                    setOnClickListener { searchFor(term) }
                }, LinearLayout.LayoutParams(-1, dp(22)).apply { bottomMargin = dp(16) })
            }
        }

        find(query)
        search.onChange { find(it) }
        search.setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            searchFor(search.text.toString())
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0); true
        }
        nav(Route.EXPLORE)
    }

    private companion object {
        const val MAX_RECENT = 10
    }
}
