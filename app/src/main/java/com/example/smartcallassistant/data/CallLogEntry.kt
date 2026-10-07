package com.example.smartcallassistant.data

data class CallLogEntry(
    val number: String,
    val timeMillis: Long,
    val modeOnCall: CallMode,
    val isIncoming: Boolean,
    val isMissed: Boolean,
    val isKnownContact: Boolean
)