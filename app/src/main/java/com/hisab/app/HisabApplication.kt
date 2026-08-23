package com.hisab.app

import android.app.Application
import com.hisab.app.di.AppContainer

class HisabApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
