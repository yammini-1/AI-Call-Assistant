package com.example.smartcallassistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat

class SmsHelper(private val context: Context) {

    private val TAG = "SmsHelper"

    fun sendSmsIfAllowed(phoneNumber: String, message: String) {
        if (phoneNumber.isBlank()) {
            Log.e(TAG, "Phone number is blank, not sending SMS")
            return
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "SEND_SMS permission not granted")
            return
        }

        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d(TAG, "SMS sent to $phoneNumber: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $phoneNumber", e)
        }
    }
}