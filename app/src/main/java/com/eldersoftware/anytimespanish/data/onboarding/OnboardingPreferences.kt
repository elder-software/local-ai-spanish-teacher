package com.eldersoftware.anytimespanish.data.onboarding

import android.content.Context

class OnboardingPreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isComplete(): Boolean = prefs.getBoolean(KEY_COMPLETE, false)

    fun setComplete(complete: Boolean) {
        prefs.edit().putBoolean(KEY_COMPLETE, complete).apply()
    }

    private companion object {
        const val PREFS_NAME = "onboarding_prefs"
        const val KEY_COMPLETE = "onboarding_complete"
    }
}
