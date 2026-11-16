package com.monicoffee.pos.core

/** Values the whole app agrees on. The limits mirror the ones the backend enforces. */
object AppConfig {
    /** The Android emulator reaches the development computer's localhost at 10.0.2.2. */
    const val DEFAULT_SERVER = "http://10.0.2.2:8000"
    const val CODE_LENGTH = 4 // PhoneVerification.CODE_LENGTH on the backend
    /** The shop phone running Moni SMS Forwarder: customers text their code here to verify. */
    const val SHOP_SMS_NUMBER = "069314234"
    const val SHOP_SMS_NUMBER_LABEL = "069\u00A0314\u00A0234" // non-breaking spaces: never split across lines
    const val MAX_QUANTITY = 50 // the backend's limit per order line
    const val MENU_MAX_AGE_MS = 30_000L
    const val ORDER_POLL_MS = 15_000L
    const val OTP_POLL_MS = 3_000L
    const val SPLASH_MS = 1200L
}
