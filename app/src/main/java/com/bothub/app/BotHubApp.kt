package com.bothub.app

import android.app.Application

class BotHubApp : Application() {
    /** Made on first use (the keystore is opened then, not at process start). */
    val session: Session by lazy { Session(this) }
}
