package com.teamji.assistantmakingproject.ui

import android.app.Application
import android.net.Uri
import android.util.Log
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
import com.teamji.assistantmakingproject.voice.BaseVoiceClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.media.MediaPlayer
import com.teamji.assistantmakingproject.voice.VoiceGender
import com.teamji.assistantmakingproject.voice.SpeechTextPlanner


data class VoiceLabUiState(
    val isRecording: Boolean = false,
    val gender: VoiceGender = VoiceGender.MALE,
    val sampleName: String? = null,
    val sampleFile: File? = null,
    val text: String = "السلام علیکم۔ یہ بنیادی آواز کا ٹیسٹ ہے۔",
    val language: ReplyLanguage = LanguagePolicy.defaultLanguage,
    val status: String = "No voice sample loaded",
    val isBusy: Boolean = false,
    val engineName: String = "",
)

class VoiceLabViewModel(application: Application) : AndroidViewModel(application) {
    private val recorder = WavAudioRecorder(application)
    private val imports = ImportedAudioStore(application)
    private val player = PcmPlayer()
    private var samplePlayer: MediaPlayer? = null
    private val baseVoice = BaseVoiceClient(application)
    private var previewJob: Job? = null
    private var generation = 0
    private val engine: VoiceCloneEngine = UnconfiguredMultilingualVoiceEngine()
    private var activeRecordingFile: File? = null

    private val _state = MutableStateFlow(VoiceLabUiState(engineName = "Base voices + experimental English cloning; Urdu cloning pending"))
    val state: StateFlow<VoiceLabUiState> = _state.asStateFlow()

    init {
        val lastSample = File(application.filesDir, "voice_samples").listFiles()
            ?.filter { it.extension == "wav" && it.length() > 44L }?.maxByOrNull { it.lastModified() }
        if (lastSample != null) _state.update {
            it.copy(sampleFile = lastSample, sampleName = lastSample.name, status = "Saved voice sample restored")
        }
    }

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
            runCatching { withContext(Dispatchers.IO) { imports.copyIntoPrivateStorage(uri) } }
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

    fun setGender(gender: VoiceGender) = _state.update { it.copy(gender = gender) }

    fun playSample() {
        val sample = _state.value.sampleFile ?: return
        if (_state.value.isBusy || _state.value.isRecording) return
        stop()
        runCatching {
            val media = MediaPlayer()
            samplePlayer = media
            media.apply {
                setDataSource(sample.absolutePath)
                setOnCompletionListener {
                    it.release()
                    samplePlayer = null
                    _state.update { state -> state.copy(isBusy = false, status = "Sample playback complete") }
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    samplePlayer = null
                    _state.update { state -> state.copy(isBusy = false, status = "Sample playback failed") }
                    true
                }
                prepare()
                start()
            }
            _state.update { it.copy(isBusy = true, status = "Playing your recorded sample") }
        }.onFailure {
            samplePlayer?.release()
            samplePlayer = null
            _state.update { state -> state.copy(isBusy = false, status = "Unable to play sample: ${it.message}") }
        }
    }

    fun updateText(value: String) = _state.update { it.copy(text = value) }

    fun setLanguage(language: ReplyLanguage) {
        _state.update {
            val suggested = if (language == ReplyLanguage.URDU) {
                "السلام علیکم۔ یہ بنیادی آواز کا ٹیسٹ ہے۔"
            } else {
                "Hello. This is an offline base voice test."
            }
            val defaults = setOf(
                "السلام علیکم۔ یہ بنیادی آواز کا ٹیسٹ ہے۔",
                "Hello. This is an offline base voice test.",
                "السلام علیکم۔ یہ میری مقامی کلون آواز کا ٹیسٹ ہے۔",
                "Hello. This is a local test of my cloned voice.",
            )
            it.copy(language = language, text = if (it.text.isBlank() || it.text in defaults) suggested else it.text)
        }
    }

    fun previewBaseVoice() {
        val snapshot = _state.value
        if (snapshot.isBusy || snapshot.isRecording || snapshot.text.isBlank()) return
        val requestId = ++generation
        previewJob = viewModelScope.launch {
            _state.update { it.copy(isBusy = true, status = "Preparing base voice (not cloned)…") }
            runCatching {
                val parts = SpeechTextPlanner.plan(snapshot.text, snapshot.language)
                for ((index, part) in parts.withIndex()) {
                    if (generation != requestId) throw CancellationException("Stopped")
                    _state.update { it.copy(status = "Preparing speech ${index + 1}/${parts.size}…") }
                    val audio = baseVoice.synthesize(part.text, part.language, snapshot.gender)
                    if (generation != requestId) throw CancellationException("Stopped")
                    _state.update { it.copy(status = "Reading ${index + 1}/${parts.size} — ${part.language.name}") }
                    player.playAndWait(audio.pcm16, audio.sampleRateHz)
                }
            }
                .onSuccess {
                    if (generation == requestId) {
                        _state.update { it.copy(isBusy = false, status = "Reading complete") }
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException && error !is TimeoutCancellationException) throw error
                    Log.e("VoiceLab", "Base voice synthesis/playback failed", error)
                    if (generation == requestId)
                        _state.update {
                            it.copy(
                                isBusy = false,
                                status = "Base voice failed: ${error.javaClass.simpleName}: ${error.message ?: "Unknown error"}",
                            )
                        }
                }
        }
    }

    fun speak() {
        val snapshot = _state.value
        if (snapshot.isBusy || snapshot.isRecording) return
        val sample = snapshot.sampleFile
        if (sample == null) {
            _state.update { it.copy(status = "پہلے اپنی آواز ریکارڈ یا امپورٹ کریں") }
            return
        }
        val parts = runCatching { SpeechTextPlanner.plan(snapshot.text, snapshot.language) }.getOrElse { error ->
            _state.update { it.copy(status = error.message ?: "Enter text") }
            return
        }
        if (snapshot.language != ReplyLanguage.ENGLISH || parts.any { it.language != ReplyLanguage.ENGLISH }) {
            _state.update { it.copy(status = "کلوننگ کا یہ تجربہ صرف انگلش کے لیے ہے۔ اردو کے لیے Base Voice استعمال کریں۔") }
            return
        }
        val requestId = ++generation
        previewJob = viewModelScope.launch {
            _state.update { it.copy(isBusy = true, status = "Preparing experimental English clone…") }
            try {
                for ((index, part) in parts.withIndex()) {
                    _state.update { it.copy(status = "Cloning ${index + 1}/${parts.size} — please wait…") }
                    val audio = baseVoice.synthesize(part.text, part.language, reference = sample)
                    if (generation != requestId) throw CancellationException("Stopped")
                    _state.update { it.copy(status = "Playing experimental cloned speech") }
                    player.playAndWait(audio.pcm16, audio.sampleRateHz)
                }
                if (generation == requestId) _state.update { it.copy(isBusy = false, status = "English clone test complete — check voice similarity") }
            } catch (error: Throwable) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                Log.e("VoiceLab", "Cloning failed", error)
                if (generation == requestId) _state.update { it.copy(isBusy = false, status = "Clone test failed: ${error.message}") }
            }
        }
    }

    fun stop() {
        generation++
        samplePlayer?.release()
        samplePlayer = null
        previewJob?.cancel()
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
        samplePlayer?.release()
        samplePlayer = null
        baseVoice.stop()
        CoroutineScope(Dispatchers.IO).launch { baseVoice.release() }
        player.stop()
        engine.release()
        super.onCleared()
    }
}
