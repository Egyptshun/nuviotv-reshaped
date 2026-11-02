package com.nuvio.tv.core.format

import kotlin.math.abs

/** Formats TMDB budget/revenue (whole US dollars; 0 means unknown). */
fun formatUsdAmountForDisplay(amount: Long?): String? {
    if (amount == null || amount <= 0L) return null
    return "$" + groupThousands(amount)
}

private fun groupThousands(value: Long): String {
    val digits = abs(value).toString()
    val builder = StringBuilder()
    for ((index, char) in digits.withIndex()) {
        if (index > 0 && (digits.length - index) % 3 == 0) {
            builder.append(',')
        }
        builder.append(char)
    }
    return builder.toString()
}
