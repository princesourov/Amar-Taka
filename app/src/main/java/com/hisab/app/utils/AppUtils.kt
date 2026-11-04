package com.hisab.app.utils

import android.content.Context
import java.util.UUID
import kotlin.math.abs

/**
 * Every user needs a userId even before Firebase Auth is wired in (Section 3's
 * Firestore isolation depends on one). This generates a stable random id once
 * per device and reuses it on every launch, so the whole app works completely
 * offline with zero sign-in. When Phase 2 wires in real auth, this id is what
 * gets migrated to the Firebase uid on first sign-in.
 */
class LocalUserProvider(context: Context) {
    private val prefs = context.getSharedPreferences("hisab_prefs", Context.MODE_PRIVATE)

    val userId: String by lazy {
        prefs.getString(KEY_USER_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_USER_ID, it).apply()
        }
    }

    companion object {
        private const val KEY_USER_ID = "local_user_id"
    }
}

/** Renders minor units (poisha) as a "৳12,345.00"-style string. */
object CurrencyConfig {
    @Volatile var symbol: String = "৳"
}

fun formatMinorAsCurrency(minorUnits: Long, symbol: String = CurrencyConfig.symbol): String {
    val negative = minorUnits < 0
    val absValue = abs(minorUnits)
    val major = absValue / 100
    val minor = absValue % 100
    val grouped = "%,d".format(major)
    return (if (negative) "-" else "") + symbol + grouped + "." + "%02d".format(minor)
}

/** Minor units as editable text for an input field: "500" or "12.50" (no symbol, no grouping, no float math). */
fun formatAmountForInput(amountMinor: Long): String {
    val abs = kotlin.math.abs(amountMinor)
    val sign = if (amountMinor < 0) "-" else ""
    return if (abs % 100 == 0L) sign + (abs / 100) else sign + (abs / 100) + "." + "%02d".format(abs % 100)
}
