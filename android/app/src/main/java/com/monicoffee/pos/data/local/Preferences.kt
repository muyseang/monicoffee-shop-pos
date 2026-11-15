package com.monicoffee.pos.data.local

import android.content.Context
import com.monicoffee.pos.core.AppConfig
import com.monicoffee.pos.data.model.Profile

/** What the app keeps on the phone between runs (SharedPreferences). */
class Preferences(context: Context) {
    private val store = context.getSharedPreferences("moni", Context.MODE_PRIVATE)

    /** Address of the Django backend. */
    val server: String get() = store.getString(SERVER, AppConfig.DEFAULT_SERVER)!!

    /** Null goes back to the default server. */
    fun saveServer(url: String?) {
        val edit = store.edit()
        if (url == null) edit.remove(SERVER) else edit.putString(SERVER, url)
        edit.apply()
    }

    var token: String?
        get() = store.getString(TOKEN, null)
        set(value) = store.edit().putString(TOKEN, value).apply()

    var profile: Profile
        get() = Profile(
            store.getInt(ID, 0),
            store.getString(NAME, "")!!,
            store.getString(PHONE, "")!!,
            store.getString(ADDRESS, "")!!,
        )
        set(value) = store.edit().putInt(ID, value.id).putString(NAME, value.fullName)
            .putString(PHONE, value.phone).putString(ADDRESS, value.address).apply()

    /** Forgets the token and the profile; favorites and settings stay. */
    fun clearSession() {
        store.edit().remove(TOKEN).remove(ID).remove(NAME).remove(PHONE).remove(ADDRESS).apply()
    }

    /** Ids of the menu items the customer marked as favorite. */
    var favorites: Set<Int>
        get() = store.getStringSet(FAVORITES, emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()
        set(value) = store.edit().putStringSet(FAVORITES, value.map { it.toString() }.toSet()).apply()

    /** The payment method pre-selected at checkout. */
    var payment: String
        get() = store.getString(PAYMENT, "cash")!!
        set(value) = store.edit().putString(PAYMENT, value).apply()

    var onboarded: Boolean
        get() = store.getBoolean(ONBOARDED, false)
        set(value) = store.edit().putBoolean(ONBOARDED, value).apply()

    private companion object {
        const val SERVER = "server"
        const val TOKEN = "token"
        const val ID = "id"
        const val NAME = "name"
        const val PHONE = "phone"
        const val ADDRESS = "address"
        const val FAVORITES = "favorites"
        const val PAYMENT = "payment"
        const val ONBOARDED = "onboarded"
    }
}
