package com.example.localllmvoice

import android.app.Application
import com.example.localllmvoice.di.AppContainer

class SoloTalkApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
