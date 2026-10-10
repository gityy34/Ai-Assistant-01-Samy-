package com.teamji.assistantmakingproject

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teamji.assistantmakingproject.ui.VoiceLabViewModel
import com.teamji.assistantmakingproject.voice.VoiceGender
import com.teamji.assistantmakingproject.voice.ReplyLanguage
import com.teamji.assistantmakingproject.voice.VoiceDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VoiceLabScreen()
                }
            }
        }
    }
}

@Composable
private fun VoiceLabScreen(vm: VoiceLabViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val recordPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.startRecording()
        } else {
            vm.onMicrophonePermissionDenied()
        }
    }

    val importAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.importAudio(uri)
    }

    val exportSample = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri ->
        val sample = state.sampleFile
        if (uri != null && sample != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        sample.inputStream().use { it.copyTo(output) }
                    } ?: error("Unable to save recording")
                }
            }
            Toast.makeText(context, if (result.isSuccess) "Recording saved" else "Saving failed", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Assistant Voice Lab", style = MaterialTheme.typography.headlineMedium)
        Text("Demo 1 • Offline voice-cloning foundation")

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = !state.isBusy,
                onClick = {
                    if (state.isRecording) {
                        vm.stopRecording()
                    } else {
                        if (
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            vm.startRecording()
                        } else {
                            recordPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            ) {
                Text(if (state.isRecording) "Stop Recording" else "Record Voice")
            }

            Button(enabled = !state.isBusy && !state.isRecording, onClick = { importAudio.launch(arrayOf("audio/wav", "audio/x-wav")) }) {
                Text("Import Voice")
            }
        }

        Text("Voice status: ${state.status}")
        Text("کلوننگ کے لیے 8–12 سیکنڈ صاف آواز ریکارڈ کریں۔ فارمیٹ: mono 16 kHz PCM WAV")
        state.sampleName?.let {
            Text("Sample: $it")
            Text("آپ کی ریکارڈنگ محفوظ ہے۔ سنیں یا WAV فائل اپنے فون میں محفوظ کریں۔")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::playSample, enabled = !state.isBusy && !state.isRecording) { Text("نمونہ سنیں") }
                Button(onClick = { exportSample.launch(it) }, enabled = !state.isBusy && !state.isRecording) { Text("Save WAV") }
            }
        }
        Text("Engine: ${state.engineName}")

        Text("Reply / speech language")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.language == ReplyLanguage.URDU,
                onClick = { vm.setLanguage(ReplyLanguage.URDU) },
                label = { Text("Urdu (default)") },
            )
            FilterChip(
                selected = state.language == ReplyLanguage.ENGLISH,
                onClick = { vm.setLanguage(ReplyLanguage.ENGLISH) },
                label = { Text("English") },
            )
        }

        Text("آواز / Voice")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.gender == VoiceGender.MALE, onClick = { vm.setGender(VoiceGender.MALE) }, enabled = !state.isBusy, label = { Text("مرد / Male") })
            FilterChip(selected = state.gender == VoiceGender.FEMALE, onClick = { vm.setGender(VoiceGender.FEMALE) }, enabled = !state.isBusy, label = { Text("عورت / Female") })
        }
        Text("ملے ہوئے متن میں اردو اور انگلش کے حصے اپنی زبان کی آواز میں پڑھے جائیں گے۔")

        OutlinedTextField(
            value = state.text,
            onValueChange = vm::updateText,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            label = { Text("Text to speak") },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = vm::speak, enabled = !state.isBusy && !state.isRecording) {
                Text("Clone Voice (English test)")
            }
            Button(onClick = vm::stop) {
                Text("Stop")
            }
        }

        Button(onClick = vm::previewBaseVoice, enabled = !state.isBusy && !state.isRecording) {
            Text("بنیادی آواز کا ٹیسٹ / Base voice")
        }
        Text("Urdu: Fasih / Aegis. English: Lessac / HFC Female. Base voices do not use your recording. English cloning is a separate experimental test.")
        Button(enabled = !state.isBusy, onClick = {
            scope.launch {
                val diagnostics = withContext(Dispatchers.IO) { VoiceDiagnostics.collect(context) }
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Voice diagnostics", diagnostics + "\nStatus: " + state.status))
                Toast.makeText(context, "Voice diagnostics copied", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("خرابی کی تفصیل کاپی کریں / Copy diagnostics")
        }

        Text(
            "Privacy: voice samples are copied to app-private storage. " +
                "The microphone runs only after you tap Record Voice."
        )
    }
}
