package com.example.smartcallassistant

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.telephony.PhoneStateListener
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.smartcallassistant.call.CallLogger
import com.example.smartcallassistant.data.CallLogEntry
import com.example.smartcallassistant.data.CallMode
import com.example.smartcallassistant.data.ModeRepository
import com.example.smartcallassistant.ui.theme.SmartCallAssistantTheme
import java.text.SimpleDateFormat
import java.util.Date
import android.speech.tts.TextToSpeech
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    private lateinit var modeRepository: ModeRepository
    private lateinit var callLogger: CallLogger
    private lateinit var telephonyManager: TelephonyManager

    private lateinit var telecomManager: TelecomManager

    private lateinit var audioManager: AudioManager

    private var phoneStateListener: PhoneStateListener? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        Log.d(TAG, "Permissions result: $result")
        // Yahan agar zaroorat ho to check kar sakte ho granted/denied
    }

    // TTS
    private lateinit var tts: TextToSpeech
    private var isTtsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        modeRepository = ModeRepository(this)
        callLogger = CallLogger(this)
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        requestNeededPermissions()
        startListeningForCalls()

        // TTS init
        tts = TextToSpeech(this) { status ->
            isTtsReady = status == TextToSpeech.SUCCESS
            if (isTtsReady) {
                val result = tts.setLanguage(Locale("hi", "IN"))
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    tts.setLanguage(Locale.ENGLISH)
                }
            } else {
                Log.e(TAG, "TTS initialization failed")
            }
        }

        val initialMode = modeRepository.getMode()

        setContent {
            SmartCallAssistantTheme {
                var currentMode by remember { mutableStateOf(initialMode) }
                var logs by remember { mutableStateOf(callLogger.entries.toList()) }

                // Simple trick: har baar Activity resume ya call log update par logs State manually update karenge
                LaunchedEffect(Unit) {
                    logs = callLogger.entries.toList()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        currentMode = currentMode,
                        onModeChange = { newMode ->
                            currentMode = newMode
                            modeRepository.setMode(newMode)
                        },
                        logs = logs,
                        onSpeak = { text -> speak(text) },
                        onSpeakForUnknownCurrentMode = {
                            speak(AssistantPhrases.unknownForMode(currentMode))
                        },
                        onSpeakKnownStudy = { speak(AssistantPhrases.knownStudy()) },
                        onSpeakKnownSleep = { speak(AssistantPhrases.knownSleep()) },
                        onSpeakQuickWho = { speak(AssistantPhrases.quickWho()) },
                        onSpeakQuickWhy = { speak(AssistantPhrases.quickWhy()) },
                        onOpenInCallAssistant = {
                            openInCallAssistant()
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        // Compose ke state ke through logs update karna hai,
        // simple tarike se: callLogger.entries state use karenge.
        // Isko perfect banane ke liye ho sakta hai hume ViewModel use karna pade,
        // par abhi ke liye simple rakhenge: activity recreate hone par naya state aayega.
    }

    override fun onDestroy() {
        super.onDestroy()
        phoneStateListener?.let {
            telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        if (isTtsReady) {
            tts.stop()
            tts.shutdown()
        }
    }

    private fun speak(text: String) {
        if (!isTtsReady) {
            Log.e(TAG, "TTS not ready")
            return
        }
        Log.d(TAG, "Speaking: $text")
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant_speech")
    }
    private fun startListeningForCalls() {
        Log.d(TAG, "startListeningForCalls called")

        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                super.onCallStateChanged(state, phoneNumber)

                Log.d(TAG, "onCallStateChanged: newState=$state number=$phoneNumber")

                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        Log.d(TAG, "CALL_STATE_RINGING detected")
                        handleIncomingRingingCall()
                    }
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        Log.d(TAG, "CALL_STATE_OFFHOOK (active call)")
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        Log.d(TAG, "CALL_STATE_IDLE detected, trying to log last incoming/missed call")
                        callLogger.logLastIncomingOrMissedCall()
                    }
                }
            }
        }

        telephonyManager.listen(
            phoneStateListener,
            PhoneStateListener.LISTEN_CALL_STATE
        )
    }

    private fun openInCallAssistant() {
        val intent = android.content.Intent(this, InCallAssistantActivity::class.java)
        startActivity(intent)
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ANSWER_PHONE_CALLS
        )

        val notGranted = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        Log.d(TAG, "Permissions to request: ${notGranted.toList()}")

        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted)
        }
    }
    private fun handleIncomingRingingCall() {
        val mode = modeRepository.getMode()
        Log.d(TAG, "handleIncomingRingingCall: mode=$mode")

        when (mode) {
            CallMode.NORMAL -> {
                Log.d(TAG, "NORMAL mode: not auto-answering")
            }
            CallMode.STUDY -> {
                Log.d(TAG, "STUDY mode: trying auto-answer + study message")
                tryAutoAnswerAndSpeakStudyMessage()
            }
            CallMode.SLEEP -> {
                Log.d(TAG, "SLEEP mode: not auto-answering")
            }
        }
    }

    private fun tryAutoAnswerAndSpeakStudyMessage() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ANSWER_PHONE_CALLS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "ANSWER_PHONE_CALLS permission not granted, cannot auto-answer")
            return
        }

        try {
            Log.d(TAG, "Attempting to auto-answer using TelecomManager.acceptRingingCall()")
            telecomManager.acceptRingingCall()

            // Thoda wait karo taaki call properly active ho jaye
            android.os.Handler(mainLooper).postDelayed({
                // Speakerphone ON karne ki koshish
                try {
                    audioManager.isSpeakerphoneOn = true
                    audioManager.mode = AudioManager.MODE_IN_CALL
                    Log.d(TAG, "Speakerphone turned ON for call")
                } catch (e: Exception) {
                    Log.e(TAG, "Error turning on speakerphone", e)
                }

                // Study mode message bolo
                val text = "Namaste, main call assistant bol raha hai. " +
                        "Jinse aap baat karna chahte hain, woh abhi padhai kar rahe hain. " +
                        "Agar aapka kaam bahut zaroori hai, toh kripya batayein, " +
                        "warna baad mein call karein."
                speak(text)

                // Agar chaho, message ke baad In-Call Assistant screen bhi khol sakte ho:
                // openInCallAssistant()

            }, 1000) // 1 second delay
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while auto-answer", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error while trying to auto-answer", e)
        }
    }
}

@Composable
fun MainScreen(
    currentMode: CallMode,
    onModeChange: (CallMode) -> Unit,
    logs: List<CallLogEntry>,
    onSpeak: (String) -> Unit,                 // abhi bhi required hai, but yahan direct use nahi karenge
    onSpeakForUnknownCurrentMode: () -> Unit, // (agar kahin aur use ho raha ho to rehne do)
    onSpeakKnownStudy: () -> Unit,
    onSpeakKnownSleep: () -> Unit,
    onSpeakQuickWho: () -> Unit,
    onSpeakQuickWhy: () -> Unit,
    onOpenInCallAssistant: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Current Mode: ${currentMode.value}",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "Select mode:")

        Spacer(modifier = Modifier.height(8.dp))

        ModeRadioOption(
            text = "Normal",
            selected = currentMode == CallMode.NORMAL,
            onClick = { onModeChange(CallMode.NORMAL) }
        )

        ModeRadioOption(
            text = "Study",
            selected = currentMode == CallMode.STUDY,
            onClick = { onModeChange(CallMode.STUDY) }
        )

        ModeRadioOption(
            text = "Sleep",
            selected = currentMode == CallMode.SLEEP,
            onClick = { onModeChange(CallMode.SLEEP) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when (currentMode) {
                CallMode.NORMAL -> "Normal mode: sab calls normal tarike se aayenge."
                CallMode.STUDY -> "Study mode: padhai ke time calls smart tarike se handle karenge."
                CallMode.SLEEP -> "Sleep mode: sirf zaroori calls ko importance milegi."
            }
        )

        // Assistant controls – ab sirf ek button
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Assistant Voice Controls:",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onOpenInCallAssistant
        ) {
            Text("Open In-Call Assistant Screen")
        }

        // Call logs below, with some spacing
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Call Logs:",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Text(text = "Abhi tak koi call log nahi.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs) { entry ->
                    CallLogItem(entry = entry)
                }
            }
        }
    }
}

@Composable
fun ModeRadioOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text)
    }
}

@Composable
fun CallLogItem(entry: CallLogEntry) {
    val sdf = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val dateStr = sdf.format(Date(entry.timeMillis))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(text = "Number: ${entry.number}")
        Text(
            text = "Time: $dateStr  |  Mode: ${entry.modeOnCall.value}  |  Known: ${entry.isKnownContact}"
        )
        Text(
            text = when {
                entry.isIncoming -> "Type: Incoming"
                entry.isMissed -> "Type: Missed"
                else -> "Type: Other"
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
}