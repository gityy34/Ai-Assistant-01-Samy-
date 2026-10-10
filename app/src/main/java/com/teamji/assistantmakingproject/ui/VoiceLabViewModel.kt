package com.teamji.assistantmakingproject.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teamji.assistantmakingproject.audio.ImportedAudioStore
import com.teamji.assistantmakingproject.audio.PcmPlayer
import com.teamji.assistantmakingproject.audio.WavAudioRecorder
import com.teamji.assistantmakingproject.voice.LanguagePolicy
import com.teamji.assistantmakingproject.voice.ReplyLanguage
import com.teamji.assistantmakingproject.voice.SynthesisRequest
import com.teamji.assistantmakingproject.voice.UnconfiguredMultilingualVoiceEngine
import com.teamji.assistantmakingproject.voice.VoiceCloneEngine
import com.teamji.assistantmakingproject.voice.VoiceReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.teamji.assistantmakingproject.voice.PiperBaseVoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File


data class VoiceLabUiState(
    val isRecording: Boolean = false,
    val sampleName: String? = null,
    val sampleFile: File? = null,
    val text: String = "السلام علیکم۔ یہ میری مقامی کلون آواز کا ٹیسٹ ہے۔",
    val language: ReplyLanguage = LanguagePolicy.defaultLanguage,
    val status: String = "No voice sample loaded",
    val isBusy: Boolean = false,
    val engineName: String = "",
)

class VoiceLabViewModel(application: Application) : AndroidViewModel(application) {
    private val recorder = WavAudioRecorder(application)
    private val imports = ImportedAudioStore(application)
    private val player = PcmPlayer()
    private val baseVoice = PiperBaseVoice(application)
    private var generation = 0
    private val engine: VoiceCloneEngine = UnconfiguredMultilingualVoiceEngine()
    private var activeRecordingFile: File? = null

    private val _state = MutableStateFlow(VoiceLabUiState(engineName = engine.engineName))
    val state: StateFlow<VoiceLabUiState> = _state.asStateFlow()

    fun startRecording() {
        val output = File(getApplication<Application>().filesDir, "voice_samples/recorded_${System.currentTimeMillis()}.wav")
        runCatching {
            recorder.start(viewModelScope, output)
            activeRecordingFile = output
            _state.update { it.copy(isRecording = true, status = "Recording… microphone is active") }
        }.onFailure { error ->
            activeRecordingFile = null
            _state.update { it.copy(status = error.message ?: "Unable to start recording") }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            recorder.stop()
            val recordedFile = activeRecordingFile?.takeIf { it.isFile && it.length() > 44L }
            activeRecordingFile = null
            _state.update {
                it.copy(
                    isRecording = false,
                    sampleFile = recordedFile,
                    sampleName = recordedFile?.name,
                    status = if (recordedFile != null) "Voice sample ready" else "Recording failed",
                )
            }
        }
    }

    fun onMicrophonePermissionDenied() {
        _state.update { it.copy(status = "Microphone permission was denied") }
    }

    fun importAudio(uri: Uri) {
        viewModelScope.launch {
            runCatching { imports.copyIntoPrivateStorage(uri) }
                .onSuccess { file ->
                    _state.update {
                        it.copy(sampleFile = file, sampleName = file.name, status = "Imported voice sample ready")
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(status = error.message ?: "Import failed") }
                }
        }
    }

    fun updateText(value: String) = _state.update { it.copy(text = value) }

    fun setLanguage(language: ReplyLanguage) {
        _state.update {
            val suggested = if (language == ReplyLanguage.URDU) {
                "السلام علیکم۔ یہ میری مقامی کلون آواز کا ٹیسٹ ہے۔"
            } else {
                "Hello. This is a local test of my cloned voice."
            }
            it.copy(language = language, text = suggested)
        }
    }

    fun previewBaseVoice() {
        val snapshot = _state.value
        if (snapshot.isBusy || snapshot.isRecording || snapshot.text.isBlank()) return
        val requestId = ++generation
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, status = "Preparing base voice (not cloned)…") }
            runCatching { baseVoice.synthesize(snapshot.text, snapshot.language) }
                .onSuccess { audio ->
                    if (generation == requestId) {
                        player.play(audio.pcm16, audio.sampleRateHz)
                        _state.update { it.copy(isBusy = false, status = "Playing base voice — not cloned") }
                    }
                }
                .onFailure { error ->
                    if (generation == requestId)
                        _state.update { it.copy(isBusy = false, status = error.message ?: "Base voice failed") }
                }
        }
    }

    fun speak() {
        val snapshot = _state.value
        val sample = snapshot.sampleFile
        if (sample == null) {
            _state.update { it.copy(status = "Record or import a voice sample first") }
            return
        }
        if (snapshot.text.isBlank()) {
            _state.update { it.copy(status = "Enter text to speak") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, status = "Preparing local cloned speech…") }
            runCatching {
                engine.initialize()
                engine.synthesize(
                    SynthesisRequest(
                        text = snapshot.text,
                        language = snapshot.language,
                        reference = VoiceReference(sample, snapshot.sampleName ?: "Voice sample"),
                    )
                )
            }.onSuccess { result ->
                player.play(result.pcm16, result.sampleRateHz)
                _state.update { it.copy(isBusy = false, status = "Playing") }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        status = error.message ?: "Voice synthesis failed",
                    )
                }
            }
        }
    }

    fun stop() {
        generation++
        baseVoice.stop()
        if (_state.value.isRecording) {
            stopRecording()
        }
        engine.stop()
        player.stop()
        _state.update { it.copy(isBusy = false, status = "Stopped") }
    }

    override fun onCleared() {
        generation++
        baseVoice.stop()
        CoroutineScope(Dispatchers.IO).launch { baseVoice.release() }
        player.stop()
        engine.release()
        super.onCleared()
    }
}
