package org.qosp.notes.ui.utils

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red

fun @receiver:ColorInt Int.applyMask(@ColorInt mask: Int): Int {
    val pctA = (mask.alpha.toFloat() / 255)
    val pctB = 1 - pctA

    val newRed = pctB * this.red + pctA * mask.red
    val newGreen = pctB * this.green + pctA * mask.green
    val newBlue = pctB * this.blue + pctA * mask.blue

    return Color.rgb(newRed.toInt(), newGreen.toInt(), newBlue.toInt())
}
