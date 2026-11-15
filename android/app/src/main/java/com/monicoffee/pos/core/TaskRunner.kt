package com.monicoffee.pos.core

import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.data.api.ApiException
import com.monicoffee.pos.ui.widget.alert
import com.monicoffee.pos.ui.widget.toast
import java.util.concurrent.Executors

/** Runs network calls off the main thread and delivers their results back on it. */
class TaskRunner(private val app: MainActivity) {
    private val io = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())
    private var timer: Runnable? = null
    private var wait: AlertDialog? = null

    /** Runs [work] off the main thread for the screen being shown; the reply is dropped if the screen changed. */
    fun <T> load(
        work: () -> T,
        sticky: Boolean = false,
        failed: (ApiException) -> Unit = { app.toast(it.message) },
        done: (T) -> Unit,
    ) {
        val asked = app.router.generation
        io.execute {
            try {
                val result = work()
                main.post { if (!app.isDestroyed && (sticky || asked == app.router.generation)) done(result) }
            } catch (e: Exception) {
                val error = e as? ApiException ?: ApiException(0, "The server sent an unexpected response.")
                main.post {
                    if (app.isDestroyed) return@post
                    if (error.status == 401 && app.session.signedIn) app.session.expire()
                    else if (sticky || asked == app.router.generation) failed(error)
                }
            }
        }
    }

    /** Runs an action the customer asked for (place order, save…) behind a "please wait" dialog. */
    fun <T> submit(work: () -> T, done: (T) -> Unit) {
        val dialog = AlertDialog.Builder(app).setMessage("Please wait…").setCancelable(false).show()
        wait = dialog
        load(work, sticky = true, failed = { dialog.dismiss(); app.alert(it.message) }) { dialog.dismiss(); done(it) }
    }

    /** Repeats [tick] until the next screen is shown. */
    fun every(millis: Long, tick: () -> Unit) {
        val task = object : Runnable {
            override fun run() { tick(); main.postDelayed(this, millis) }
        }
        timer = task
        main.postDelayed(task, millis)
    }

    fun later(millis: Long, action: () -> Unit) {
        main.postDelayed(action, millis)
    }

    fun stopTimer() {
        timer?.let(main::removeCallbacks)
        timer = null
    }

    fun dismissWait() {
        wait?.dismiss()
    }

    fun shutdown() {
        io.shutdown()
    }
}
