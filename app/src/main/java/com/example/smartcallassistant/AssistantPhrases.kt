package com.example.smartcallassistant

import com.example.smartcallassistant.data.CallMode

object AssistantPhrases {

    fun unknownForMode(mode: CallMode): String =
        when (mode) {
            CallMode.NORMAL -> unknownNormal()
            CallMode.STUDY -> unknownStudy()
            CallMode.SLEEP -> unknownSleep()
        }

    fun unknownNormal(): String =
        "Namaste, main aapka call assistant bol raha hu. " +
                "Aap kaun bol rahe hain, aur kis kaam se call kiya hai?"

    fun unknownStudy(): String =
        "Namaste, abhi main padhai kar raha hu. " +
                "Agar aapka kaam bahut zaroori hai, toh boliye: urgent hai. " +
                "Warna kripya baad mein call karein."

    fun unknownSleep(): String =
        "Namaste, abhi user so rahe hain. " +
                "Agar emergency hai, toh boliye: bahut zaroori hai. " +
                "Warna kripya baad main call karein."

    fun knownStudy(): String =
        "Namaste, abhi main padhai kar raha hoon. " +
                "Agar urgent nahi hai, toh kripya baad mein call karna."

    fun knownSleep(): String =
        "Namaste, abhi main so raha hoon. " +
                "Agar emergency nahi hai, toh kripya baad main call karein."

    fun quickWho(): String = "Aap kaun bol rahe hain?"
    fun quickWhy(): String = "Aapne kis kaam se call kiya hai?"
}