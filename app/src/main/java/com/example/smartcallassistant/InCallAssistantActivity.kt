package com.example.smartcallassistant

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smartcallassistant.data.CallMode
import com.example.smartcallassistant.data.ModeRepository
import com.example.smartcallassistant.ui.theme.SmartCallAssistantTheme
import java.util.Locale

class InCallAssistantActivity : ComponentActivity() {

    private val TAG = "InCallAssistantActivity"

    private lateinit var modeRepo: ModeRepository
    private lateinit var tts: TextToSpeech
    private var isTtsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        modeRepo = ModeRepository(this)

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
                Log.e(TAG, "TTS init failed")
            }
        }

        setContent {
            SmartCallAssistantTheme {
                val currentMode = remember { mutableStateOf(modeRepo.getMode()) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InCallAssistantScreen(
                        currentMode = currentMode.value,
                        onRefreshMode = {
                            currentMode.value = modeRepo.getMode()
                        },
                        onSpeak = { speak(it) },
                        onSpeakUnknownCurrentMode = {
                            speak(AssistantPhrases.unknownForMode(currentMode.value))
                        },
                        onSpeakKnownStudy = { speak(AssistantPhrases.knownStudy()) },
                        onSpeakKnownSleep = { speak(AssistantPhrases.knownSleep()) },
                        onSpeakQuickWho = { speak(AssistantPhrases.quickWho()) },
                        onSpeakQuickWhy = { speak(AssistantPhrases.quickWhy()) }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
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
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "in_call_assistant")
    }
}

@Composable
fun InCallAssistantScreen(
    currentMode: CallMode,
    onRefreshMode: () -> Unit,
    onSpeak: (String) -> Unit,
    onSpeakUnknownCurrentMode: () -> Unit,
    onSpeakKnownStudy: () -> Unit,
    onSpeakKnownSleep: () -> Unit,
    onSpeakQuickWho: () -> Unit,
    onSpeakQuickWhy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "In-Call Assistant",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Current Mode: ${currentMode.value}")
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onRefreshMode) {
                Text("Refresh")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { onSpeak("Namaste, main aapka call assistant hu.") }) {
            Text("Test Voice")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Unknown Caller:",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = onSpeakUnknownCurrentMode) {
            Text("Unknown (current mode message)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Known Caller:",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = onSpeakKnownStudy) {
            Text("Busy (Study message)")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onSpeakKnownSleep) {
            Text("Sleeping (Sleep message)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Quick phrases:",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSpeakQuickWho,
                modifier = Modifier.weight(1f)
            ) {
                Text("Kaun bol rahe hain?")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSpeakQuickWhy,
                modifier = Modifier.weight(1f)
            ) {
                Text("Kis kaam se call kiya?")
            }
        }
    }
}