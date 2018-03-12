package com.dhpcs.liquidity.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.*

class Identicon @JvmOverloads constructor(context: Context,
                                          attrs: AttributeSet? = null,
                                          defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {

        private const val COLOR_SATURATION = 1f
        private const val COLOR_VALUE = 0.8f

        private const val GRID_ROW_COUNT = 5
        private const val GRID_COLUMN_COUNT = 5
        private const val GRID_CENTER_COLUMN_INDEX = GRID_COLUMN_COUNT / 2

        private fun getCellColors(hash: ByteArray): Array<IntArray> {
            val color = getCellColor(hash)
            val result = Array(GRID_ROW_COUNT) { IntArray(GRID_COLUMN_COUNT) }
            (0 until GRID_ROW_COUNT).forEach { r ->
                (0 until GRID_COLUMN_COUNT)
                        .filter { isCellVisible(hash, r, it) }
                        .forEach { result[r][it] = color }
            }
            return result
        }

        private fun getCellColor(hash: ByteArray): Int {
            val hueMsb = getModuloColorHashByte(hash, 0).toInt()
            val hueLsb = getModuloColorHashByte(hash, 1).toInt()
            val hue = ((0xFF and hueMsb shl 8 or (0xFF and hueLsb)) / 65536.0 * 360.0).toFloat()
            return Color.HSVToColor(floatArrayOf(hue, COLOR_SATURATION, COLOR_VALUE))
        }

        private fun isCellVisible(hash: ByteArray, row: Int, column: Int): Boolean {
            return getModuloShapeHashBit(
                    hash,
                    row * GRID_CENTER_COLUMN_INDEX + 1 + getSymmetricColumnIndex(column)
            )
        }

        private fun getModuloColorHashByte(hash: ByteArray, index: Int): Byte =
                hash[index % hash.size]

        private fun getModuloShapeHashBit(hash: ByteArray, index: Int): Boolean {

            /*
             * The offset of 2 is to skip the two color determinant bytes.
             */
            val bits = hash[(2 + index / 8) % hash.size].toInt()
            return 1 and bits.ushr(index % 8) == 1
        }

        private fun getSymmetricColumnIndex(columnIndex: Int): Int =
                if (columnIndex < GRID_CENTER_COLUMN_INDEX) {
                    columnIndex
                } else {
                    GRID_COLUMN_COUNT - (columnIndex + 1)
                }

    }

    private val paint = Paint()

    private var cellColors: Array<IntArray>? = null
    private var cellWidth = 0
    private var cellHeight = 0

    fun show(zoneId: String, memberId: String) {
        val hash = MessageDigest.getInstance("SHA-1")

        try {
            val zoneIdAsUuid = UUID.fromString(zoneId)
            hash.update(
                    ByteBuffer.allocate(8)
                            .putLong(zoneIdAsUuid.mostSignificantBits).array()
            )
            hash.update(
                    ByteBuffer.allocate(8)
                            .putLong(zoneIdAsUuid.leastSignificantBits).array()
            )
        } catch (_: IllegalArgumentException) {
            hash.update(zoneId.toByteArray())
        }

        try {
            val memberIdAsInt = java.lang.Long.parseLong(memberId)
            if (memberIdAsInt <= Integer.MAX_VALUE) {
                hash.update(
                        ByteBuffer.allocate(4).putInt(memberIdAsInt.toInt()).array()
                )
            } else {
                hash.update(
                        ByteBuffer.allocate(8).putLong(memberIdAsInt).array()
                )
            }
        } catch (_: IllegalArgumentException) {
            hash.update(memberId.toByteArray())
        }

        cellColors = getCellColors(hash.digest())
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cellColors != null) {
            for (rowIndex in 0 until GRID_ROW_COUNT) {
                for (columnIndex in 0 until GRID_COLUMN_COUNT) {
                    val x = cellWidth * columnIndex
                    val y = cellHeight * rowIndex
                    paint.color = cellColors!![rowIndex][columnIndex]
                    canvas.drawRect(
                            x.toFloat(),
                            y.toFloat(),
                            (x + cellWidth).toFloat(),
                            (y + cellHeight).toFloat(),
                            paint
                    )
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellWidth = w / GRID_COLUMN_COUNT
        cellHeight = h / GRID_ROW_COUNT
    }

}
