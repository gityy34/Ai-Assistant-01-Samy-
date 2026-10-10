package com.teamji.assistantmakingproject

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teamji.assistantmakingproject.ui.VoiceLabViewModel
import com.teamji.assistantmakingproject.voice.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme(
                primary = Color(0xFF77DCCB), background = Color(0xFF101A20),
                surface = Color(0xFF17262D), secondaryContainer = Color(0xFF24443F)
            ) else lightColorScheme(
                primary = Color(0xFF006B5D), onPrimary = Color.White,
                background = Color(0xFFF4F7F8), surface = Color.White,
                secondaryContainer = Color(0xFFD9EFEA), onSecondaryContainer = Color(0xFF123B34)
            )
            MaterialTheme(colorScheme = colors) { VoiceLabScreen() }
        }
    }
}

@Composable
private fun VoiceLabScreen(vm: VoiceLabViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cloneMode by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    val locked = state.isBusy || state.isRecording
    val recordPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) vm.startRecording() else vm.onMicrophonePermissionDenied()
    }
    val importAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) vm.importAudio(it)
    }
    val exportSample = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        val sample = state.sampleFile
        if (uri != null && sample != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    sample.inputStream().use { it.copyTo(output) }
                } ?: error("Unable to save recording")
            } }
            Toast.makeText(context, if (result.isSuccess) "ریکارڈنگ محفوظ ہو گئی" else "فائل محفوظ نہیں ہوئی", Toast.LENGTH_SHORT).show()
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { if (cloneMode) vm.speak() else vm.previewBaseVoice() },
                        enabled = !locked && state.text.isNotBlank() && (!cloneMode || state.sampleFile != null),
                        modifier = Modifier.weight(1f).testTag("speak")
                    ) { Text(if (cloneMode) "کلون آزمائیں" else "متن سنیں") }
                    OutlinedButton(
                        onClick = vm::stop, enabled = locked,
                        modifier = Modifier.testTag("stop")
                    ) { Text("روکیں") }
                }
            }
        }
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).testTag("content").padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("VOICE LAB", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text("آواز آپ کے اختیار میں", style = MaterialTheme.typography.headlineSmall)
            Text("فون پر کام کرتا ہے • انٹرنیٹ کے بغیر", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !cloneMode, enabled = !locked,
                    onClick = { cloneMode = false }, label = { Text("متن پڑھیں") },
                    modifier = Modifier.weight(1f).testTag("readMode"))
                FilterChip(selected = cloneMode, enabled = !locked,
                    onClick = { cloneMode = true }, label = { Text("میری آواز") },
                    modifier = Modifier.weight(1f).testTag("cloneMode"))
            }
            Card(Modifier.fillMaxWidth().testTag("status"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (state.isRecording) "مائیکروفون چل رہا ہے" else if (state.isBusy) "کام جاری ہے" else "موجودہ حالت",
                        style = MaterialTheme.typography.titleSmall)
                    if (state.isRecording) Text("${state.recordingSeconds} / 15 سیکنڈ", style = MaterialTheme.typography.titleMedium)
                    Text(state.status, style = MaterialTheme.typography.bodyMedium)
                    if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            if (cloneMode) {
                LabCard("اپنی آواز کا نمونہ") {
                    Text("8–12 سیکنڈ صاف انگلش بولیں۔ Record نیا نمونہ بناتا ہے؛ Import پہلے سے موجود WAV لیتا ہے۔")
                    Button(onClick = {
                        if (state.isRecording) vm.stopRecording()
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                            vm.startRecording()
                        else recordPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth().testTag("record")) {
                        Text(if (state.isRecording) "ریکارڈنگ مکمل کریں" else "آواز ریکارڈ کریں")
                    }
                    OutlinedButton(onClick = { importAudio.launch(arrayOf("audio/wav", "audio/x-wav")) },
                        enabled = !locked, modifier = Modifier.fillMaxWidth().testTag("import")) { Text("WAV امپورٹ کریں") }
                    state.sampleName?.let { name ->
                        Text(name, style = MaterialTheme.typography.labelMedium)
                        Text("نمونہ ایپ میں محفوظ ہے؛ نیچے سے سنیں یا فون میں فائل محفوظ کریں۔", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = vm::playSample, enabled = !locked, modifier = Modifier.fillMaxWidth()) { Text("اپنی ریکارڈنگ سنیں") }
                        TextButton(onClick = { exportSample.launch(name) }, enabled = !locked) { Text("WAV فائل محفوظ کریں") }
                    }
                    Text("تجرباتی انگلش کلون • اردو کلون ابھی دستیاب نہیں۔ اپنی آواز سے مشابہت سن کر چیک کریں۔",
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
            LabCard(if (cloneMode) "کلون کے لیے زبان" else "زبان اور بنیادی آواز") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.language == ReplyLanguage.URDU, enabled = !locked,
                        onClick = { vm.setLanguage(ReplyLanguage.URDU) }, label = { Text("اردو") }, modifier = Modifier.testTag("urdu"))
                    FilterChip(selected = state.language == ReplyLanguage.ENGLISH, enabled = !locked,
                        onClick = { vm.setLanguage(ReplyLanguage.ENGLISH) }, label = { Text("English") }, modifier = Modifier.testTag("english"))
                }
                if (!cloneMode) {
                    Text("ہر زبان میں دو آوازیں۔ یہ آوازیں آپ کی ریکارڈنگ استعمال نہیں کرتیں۔", style = MaterialTheme.typography.bodySmall)
                    val male = if (state.language == ReplyLanguage.URDU) "Fasih · مرد" else "HFC Male"
                    val female = if (state.language == ReplyLanguage.URDU) "Aegis · عورت" else "HFC Female"
                    // Stack choices so names remain readable on narrow screens and large font sizes.
                    FilterChip(selected = state.gender == VoiceGender.MALE, enabled = !locked,
                        onClick = { vm.setGender(VoiceGender.MALE) }, label = { Text(male) }, modifier = Modifier.testTag("male"))
                    FilterChip(selected = state.gender == VoiceGender.FEMALE, enabled = !locked,
                        onClick = { vm.setGender(VoiceGender.FEMALE) }, label = { Text(female) }, modifier = Modifier.testTag("female"))
                    Text("ملے ہوئے متن کے اردو اور انگلش حصے اپنی زبان میں پڑھے جائیں گے۔ اردو تلفظ کا معیار محدود ہو سکتا ہے۔", style = MaterialTheme.typography.bodySmall)
                } else if (state.language != ReplyLanguage.ENGLISH) {
                    Text("کلون آزمانے کے لیے English منتخب کریں اور انگلش متن لکھیں۔", color = MaterialTheme.colorScheme.error)
                }
                Text("پڑھنے کی رفتار", style = MaterialTheme.typography.titleSmall)
                listOf(listOf(0.85f, 1f), listOf(1.2f, 1.4f)).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { speed ->
                            FilterChip(selected = state.speed == speed, enabled = !locked, onClick = { vm.setSpeed(speed) },
                                label = { Text("${speed}×") }, modifier = Modifier.weight(1f).testTag("speed$speed"))
                        }
                    }
                }
            }
            LabCard("کیا پڑھنا ہے؟") {
                OutlinedTextField(value = state.text, onValueChange = vm::updateText, enabled = !locked,
                    modifier = Modifier.fillMaxWidth().testTag("text"), minLines = 5, maxLines = 10,
                    label = { Text(if (cloneMode) "English text" else "اردو یا انگلش متن") },
                    supportingText = { Text("${state.text.length} / 20,000") }, isError = state.text.length > 20_000)
                Text("پہلی بار آواز تیار ہونے میں انتظار ہو سکتا ہے۔ لمبا متن حصوں میں تیار ہوتا ہے۔", style = MaterialTheme.typography.bodySmall)
            }
            state.resultLabel?.let { source ->
                LabCard("آخری تیار آواز") {
                    Text(source, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = vm::playLastResult, enabled = !locked, modifier = Modifier.fillMaxWidth()) { Text("آخری حصہ دوبارہ سنیں") }
                }
            }
            TextButton(onClick = { details = !details }, modifier = Modifier.testTag("details")) { Text(if (details) "تفصیل بند کریں" else "مدد اور خرابی کی تفصیل") }
            if (details) {
                Text("یہ مرحلہ متن پڑھنے اور تجرباتی کلون کے لیے ہے؛ سوالوں کے AI جواب ابھی شامل نہیں۔", style = MaterialTheme.typography.bodySmall)
                Text("ریکارڈنگ صرف Record دبانے کے بعد ہوتی ہے۔ نمونے ایپ کی نجی جگہ میں محفوظ ہیں؛ اَن انسٹال سے حذف ہو سکتے ہیں۔", style = MaterialTheme.typography.bodySmall)
                Text("ورژن ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall)
                OutlinedButton(enabled = !locked, onClick = {
                    scope.launch {
                        val diagnostics = withContext(Dispatchers.IO) { VoiceDiagnostics.collect(context) }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Voice diagnostics", diagnostics + "\nStatus: " + state.status))
                        Toast.makeText(context, "تفصیل کاپی ہو گئی", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("خرابی کی تفصیل کاپی کریں") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LabCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
