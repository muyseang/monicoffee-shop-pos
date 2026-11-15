package com.monicoffee.pos.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** The app's line icons and small illustrations, drawn in a 40-unit box and scaled to the view. */
class Icon(context: Context, val kind: String, val tint: Int) : View(context) {
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
