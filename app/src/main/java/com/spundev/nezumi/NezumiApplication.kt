package com.spundev.nezumi

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class NezumiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Force dark theme
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
}