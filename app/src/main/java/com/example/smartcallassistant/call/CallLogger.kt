package com.example.smartcallassistant.call

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import com.example.smartcallassistant.NotificationHelper
import com.example.smartcallassistant.SmsHelper
import com.example.smartcallassistant.data.CallLogEntry
import com.example.smartcallassistant.data.CallMode
import com.example.smartcallassistant.data.ModeRepository

class CallLogger(private val context: Context) {

    private val TAG = "CallLogger"
    private val modeRepo = ModeRepository(context)

    private val notificationHelper = NotificationHelper(context)
    private val smsHelper = SmsHelper(context)

    private val _entries = mutableListOf<CallLogEntry>()
    val entries: List<CallLogEntry> get() = _entries

    fun logLastIncomingOrMissedCall() {
        Log.d(TAG, "logLastIncomingOrMissedCall() called")

        try {
            val cursor: Cursor? = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE
                ),
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )

            if (cursor == null) {
                Log.e(TAG, "Cursor is null, maybe no permission for READ_CALL_LOG")
                return
            }

            cursor.use {
                if (it.moveToFirst()) {
                    val number = it.getString(0) ?: "Unknown"
                    val type = it.getInt(1)
                    val date = it.getLong(2)

                    Log.d(TAG, "Last call from log: number=$number type=$type date=$date")

                    val isIncoming = type == CallLog.Calls.INCOMING_TYPE
                    val isMissed = type == CallLog.Calls.MISSED_TYPE

                    if (isIncoming || isMissed) {
                        val modeAtCall = modeRepo.getMode()
                        val known = isNumberInContacts(number)

                        val entry = CallLogEntry(
                            number = number,
                            timeMillis = date,
                            modeOnCall = modeAtCall,
                            isIncoming = isIncoming,
                            isMissed = isMissed,
                            isKnownContact = known
                        )

                        _entries.add(0, entry)
                        saveToPrefs()
                        Log.d(TAG, "Logged call entry: $entry")

                        handleModeBehavior(modeAtCall, number, known)
                    } else {
                        Log.d(TAG, "Last call is not INCOMING or MISSED, type=$type, skipping")
                    }
                } else {
                    Log.d(TAG, "Call log cursor empty — no calls found")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Permission issue while reading call log", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error logging call", e)
        }
    }

    private val prefs = context.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
    private val LOG_KEY = "call_history_json"

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        try {
            val json = prefs.getString(LOG_KEY, null) ?: return
            val list = mutableListOf<CallLogEntry>()

            // Very simple JSON parsing: ek entry per line jaisa format use karenge
            // Apne hi custom format me store/parse karenge (true JSON parser nahi)
            // Format: number|timeMillis|mode|isIncoming|isMissed|isKnown

            val lines = json.split("\n")
            for (line in lines) {
                if (line.isBlank()) continue
                val parts = line.split("|")
                if (parts.size < 6) continue

                val number = parts[0]
                val time = parts[1].toLongOrNull() ?: continue
                val modeStr = parts[2]
                val incoming = parts[3].toBoolean()
                val missed = parts[4].toBoolean()
                val known = parts[5].toBoolean()

                val mode = CallMode.fromValue(modeStr)
                val entry = CallLogEntry(
                    number = number,
                    timeMillis = time,
                    modeOnCall = mode,
                    isIncoming = incoming,
                    isMissed = missed,
                    isKnownContact = known
                )
                list.add(entry)
            }

            _entries.clear()
            _entries.addAll(list)
            Log.d(TAG, "Loaded ${list.size} call log entries from prefs")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading call history from prefs", e)
        }
    }

    private fun saveToPrefs() {
        try {
            // Same format: one entry per line
            val builder = StringBuilder()
            for (entry in _entries) {
                builder.append(entry.number).append("|")
                    .append(entry.timeMillis).append("|")
                    .append(entry.modeOnCall.value).append("|")
                    .append(entry.isIncoming).append("|")
                    .append(entry.isMissed).append("|")
                    .append(entry.isKnownContact)
                    .append("\n")
            }
            prefs.edit().putString(LOG_KEY, builder.toString()).apply()
            Log.d(TAG, "Saved ${_entries.size} call log entries to prefs")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving call history to prefs", e)
        }
    }

    private fun handleModeBehavior(mode: CallMode, number: String, isKnown: Boolean) {
        when (mode) {
            CallMode.NORMAL -> {
                // Normal: sirf log, koi auto-SMS nahi
            }
            CallMode.STUDY -> {
                val msg = if (isKnown) {
                    // Known contact ke liye thoda friendly message
                    "Abhi main padhai kar raha hoon. Agar emergency nahi ho to kripya baad mein call karein."
                } else {
                    // Unknown ke liye generic
                    "Abhi main padhai kar raha hoon. Agar zaroori ho to message bhej dein ya baad mein call karein."
                }

                smsHelper.sendSmsIfAllowed(number, msg)

                val title = if (isKnown) {
                    "Known call during Study"
                } else {
                    "Unknown call during Study"
                }

                notificationHelper.showNotification(
                    2001,
                    title,
                    "Call from $number while you were studying. Auto-SMS sent."
                )
            }
            CallMode.SLEEP -> {
                val msg = if (isKnown) {
                    "Abhi main so raha hoon. Agar emergency nahi ho to kripya subah call karein."
                } else {
                    "Abhi main so raha hoon. Agar emergency na ho to kripya subah call karein."
                }

                smsHelper.sendSmsIfAllowed(number, msg)

                val title = if (isKnown) {
                    "Known call during Sleep"
                } else {
                    "Unknown call during Sleep"
                }

                notificationHelper.showNotification(
                    2002,
                    title,
                    "Call from $number while you were sleeping. Auto-SMS sent."
                )
            }
        }
    }

    private fun isNumberInContacts(number: String): Boolean {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)

        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val index =
                cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val contactNumber = cursor.getString(index)
                if (normalizePhone(contactNumber) == normalizePhone(number)) {
                    return true
                }
            }
        }
        return false
    }

    private fun normalizePhone(number: String): String {
        return number.replace("[^0-9]".toRegex(), "")
            .takeLast(10)
    }
}