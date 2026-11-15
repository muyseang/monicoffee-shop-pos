package com.monicoffee.pos.ui.widget

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.widget.Toast

fun Context.toast(message: String?) {
    Toast.makeText(this, message ?: "Something went wrong", Toast.LENGTH_LONG).show()
}

fun Activity.alert(message: String?) {
    if (!isDestroyed) AlertDialog.Builder(this).setMessage(message ?: "Something went wrong").setPositiveButton("OK", null).show()
}
