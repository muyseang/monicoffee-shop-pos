package com.monicoffee.pos.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.abs

/**
 * A minimal QR Code encoder (ISO/IEC 18004): byte mode, error correction level H, versions 1–4,
 * so up to 34 bytes — enough for an "SMSTO:" message. Level H survives 30% of the code being
 * covered, which leaves room for the logo drawn on its centre. Written here because the app uses
 * no third-party libraries. Returns the modules as [row][column], true = dark.
 */
object QrCode {
    // Level H, by version (index 1–4): all codewords, error-correction codewords per block, blocks.
    private val TOTAL = intArrayOf(0, 26, 44, 70, 100)
    private val EC_PER_BLOCK = intArrayOf(0, 17, 28, 22, 16)
    private val BLOCKS = intArrayOf(0, 1, 1, 2, 4)

    fun encode(text: String): Array<BooleanArray> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val version = (1..4).firstOrNull { 12 + bytes.size * 8 <= dataCodewords(it) * 8 }
            ?: throw IllegalArgumentException("Too long for a QR code: ${bytes.size} bytes")
        return Matrix(version, addErrorCorrection(version, dataBits(version, bytes))).modules
    }

    private fun dataCodewords(version: Int) = TOTAL[version] - EC_PER_BLOCK[version] * BLOCKS[version]

    /** Mode indicator, length, the bytes, terminator and padding, as codewords. */
    private fun dataBits(version: Int, bytes: ByteArray): IntArray {
        val capacity = dataCodewords(version) * 8
        val bits = ArrayList<Boolean>(capacity)
        fun put(value: Int, count: Int) { for (i in count - 1 downTo 0) bits.add(((value ushr i) and 1) == 1) }
        put(0b0100, 4); put(bytes.size, 8); bytes.forEach { put(it.toInt() and 0xFF, 8) }
        put(0, minOf(4, capacity - bits.size))
        put(0, (8 - bits.size % 8) % 8)
        var pad = 0xEC
        while (bits.size < capacity) { put(pad, 8); pad = pad xor 0xEC xor 0x11 }
        return IntArray(bits.size / 8) { i -> (0 until 8).fold(0) { acc, j -> (acc shl 1) or (if (bits[i * 8 + j]) 1 else 0) } }
    }

    /** Splits the data into blocks, adds Reed–Solomon codewords and interleaves them. */
    private fun addErrorCorrection(version: Int, data: IntArray): IntArray {
        val blocks = BLOCKS[version]; val perBlock = data.size / blocks; val ecLength = EC_PER_BLOCK[version]
        val divisor = rsDivisor(ecLength)
        val dataBlocks = (0 until blocks).map { data.copyOfRange(it * perBlock, (it + 1) * perBlock) }
        val ecBlocks = dataBlocks.map { rsRemainder(it, divisor) }
        val out = ArrayList<Int>(TOTAL[version])
        for (i in 0 until perBlock) dataBlocks.forEach { out.add(it[i]) }
        for (i in 0 until ecLength) ecBlocks.forEach { out.add(it[i]) }
        return out.toIntArray()
    }

    private fun rsDivisor(degree: Int): IntArray {
        val result = IntArray(degree).also { it[degree - 1] = 1 }
        var root = 1
        repeat(degree) {
            for (j in 0 until degree) {
                result[j] = gfMultiply(result[j], root)
                if (j + 1 < degree) result[j] = result[j] xor result[j + 1]
            }
            root = gfMultiply(root, 0x02)
        }
        return result
    }

    private fun rsRemainder(data: IntArray, divisor: IntArray): IntArray {
        val result = IntArray(divisor.size)
        for (b in data) {
            val factor = b xor result[0]
            System.arraycopy(result, 1, result, 0, result.size - 1); result[result.size - 1] = 0
            for (i in result.indices) result[i] = result[i] xor gfMultiply(divisor[i], factor)
        }
        return result
    }

    /** Multiplication in GF(2^8) modulo x^8 + x^4 + x^3 + x^2 + 1. */
    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private class Matrix(version: Int, codewords: IntArray) {
        val size = version * 4 + 17
        val modules = Array(size) { BooleanArray(size) }
        private val function = Array(size) { BooleanArray(size) }

        init {
            drawFunctionPatterns(version)
            drawCodewords(codewords)
            // Use the mask with the lowest penalty, as the standard asks.
            val mask = (0 until 8).minByOrNull { m -> applyMask(m); drawFormatBits(m); penalty().also { applyMask(m) } }!!
            applyMask(mask); drawFormatBits(mask)
        }

        private fun set(x: Int, y: Int, dark: Boolean) { modules[y][x] = dark; function[y][x] = true }

        private fun drawFunctionPatterns(version: Int) {
            for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) } // timing
            for ((cx, cy) in listOf(3 to 3, size - 4 to 3, 3 to size - 4)) {         // finders + separators
                for (dy in -4..4) for (dx in -4..4) {
                    val x = cx + dx; val y = cy + dy
                    if (x in 0 until size && y in 0 until size) maxOf(abs(dx), abs(dy)).let { set(x, y, it != 2 && it != 4) }
                }
            }
            if (version >= 2) {                                                        // one alignment pattern
                val c = size - 7
                for (dy in -2..2) for (dx in -2..2) set(c + dx, c + dy, maxOf(abs(dx), abs(dy)) != 1)
            }
            drawFormatBits(0) // reserve the format areas; drawn for real once the mask is chosen
        }

        /** Level H (format bits 10) and the mask, BCH-protected, in both copies. */
        private fun drawFormatBits(mask: Int) {
            val data = (0b10 shl 3) or mask
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = ((data shl 10) or rem) xor 0x5412
            fun bit(i: Int) = ((bits ushr i) and 1) == 1
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
            for (i in 9..14) set(14 - i, 8, bit(i))
            for (i in 0..7) set(size - 1 - i, 8, bit(i))
            for (i in 8..14) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true) // the always-dark module
        }

        /** Places the codeword bits in the zigzag order, skipping function patterns. */
        private fun drawCodewords(codewords: IntArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) for (j in 0..1) {
                    val x = right - j
                    val y = if (((right + 1) and 2) == 0) size - 1 - vert else vert
                    if (!function[y][x] && i < codewords.size * 8) {
                        modules[y][x] = ((codewords[i ushr 3] ushr (7 - (i and 7))) and 1) == 1; i++
                    }
                }
                right -= 2
            }
        }

        private fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                if (function[y][x]) continue
                val flip = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (flip) modules[y][x] = !modules[y][x]
            }
        }

        /** Penalty rules N1–N4: long runs, 2×2 blocks, finder look-alikes, dark/light balance. */
        private fun penalty(): Int {
            var score = 0
            val finderLike = listOf(
                booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false),
                booleanArrayOf(false, false, false, false, true, false, true, true, true, false, true),
            )
            for (line in 0 until 2) for (a in 0 until size) {
                fun at(b: Int) = if (line == 0) modules[a][b] else modules[b][a]
                var run = 1
                for (b in 1 until size) {
                    if (at(b) == at(b - 1)) run++ else { if (run >= 5) score += run - 2; run = 1 }
                }
                if (run >= 5) score += run - 2
                for (b in 0..size - 11) if (finderLike.any { p -> p.indices.all { at(b + it) == p[it] } }) score += 40
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) score += 3
            }
            val dark = modules.sumOf { row -> row.count { it } }
            val total = size * size
            score += ((abs(dark * 20 - total * 10) + total - 1) / total - 1) * 10
            return score
        }
    }
}

/**
 * Draws a QR code inside a light quiet zone of [quiet] modules (white unless a background is set).
 * The standard asks for 4; a narrower one is fine when light space continues around the view.
 */
class QrView(context: Context, private val modules: Array<BooleanArray>, private val quiet: Int = 4, darkColor: Int = Color.BLACK) : View(context) {
    private val dark = Paint().apply { color = darkColor }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (background == null) c.drawColor(Color.WHITE)
        val n = modules.size + quiet * 2
        val cell = minOf(width, height) / n
        val offset = (minOf(width, height) - cell * n) / 2 + cell * quiet
        for (y in modules.indices) for (x in modules.indices) {
            if (modules[y][x]) c.drawRect((offset + x * cell).toFloat(), (offset + y * cell).toFloat(), (offset + (x + 1) * cell).toFloat(), (offset + (y + 1) * cell).toFloat(), dark)
        }
    }
}
