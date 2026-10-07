package com.example.smartcallassistant.data

enum class CallMode(val value: String) {
    NORMAL("NORMAL"),
    STUDY("STUDY"),
    SLEEP("SLEEP");

    companion object {
        fun fromValue(v: String?): CallMode {
            return values().find { it.value == v } ?: NORMAL
        }
    }
}