package com.example.smartcallassistant.data

import android.content.Context
import android.content.SharedPreferences

class ModeRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)

    fun getMode(): CallMode {
        val value = prefs.getString("current_mode", CallMode.NORMAL.value)
        return CallMode.fromValue(value)
    }

    fun setMode(mode: CallMode) {
        prefs.edit().putString("current_mode", mode.value).apply()
    }
}