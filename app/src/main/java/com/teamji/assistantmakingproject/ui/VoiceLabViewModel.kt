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
import com.teamji.assistantmakingproject.voice.SpeechQueue


data class VoiceLabUiState(
    val isRecording: Boolean = false,
    val recordingSeconds: Int = 0,
    val gender: VoiceGender = VoiceGender.MALE,
    val speed: Float = 1f,
    val resultLabel: String? = null,
    val sampleName: String? = null,
    val sampleFile: File? = null,
    val text: String = "السلام علیکم۔ یہ بنیادی آواز کا ٹیسٹ ہے۔",
    val language: ReplyLanguage = LanguagePolicy.defaultLanguage,
    val status: String = "نمونہ ابھی ریکارڈ نہیں ہوا؛ بنیادی آواز استعمال کر سکتے ہیں",
    val isBusy: Boolean = false,
    val engineName: String = "",
)

class VoiceLabViewModel(application: Application) : AndroidViewModel(application) {
    private val recorder = WavAudioRecorder(application)
    private val imports = ImportedAudioStore(application)
    private val player = PcmPlayer()
    private var samplePlayer: MediaPlayer? = null
    private var lastOutput: com.teamji.assistantmakingproject.voice.SynthesisResult? = null
    private val baseVoice = BaseVoiceClient(application)
    private var previewJob: Job? = null
    private var generation = 0
    private var activeRecordingFile: File? = null
    private var recordingTimer: Job? = null

    private val _state = MutableStateFlow(VoiceLabUiState(engineName = "Base voices + experimental English cloning; Urdu cloning pending"))
    val state: StateFlow<VoiceLabUiState> = _state.asStateFlow()

    init {
        val lastSample = File(application.filesDir, "voice_samples").listFiles()
            ?.filter { it.extension == "wav" && it.length() > 44L }?.maxByOrNull { it.lastModified() }
        if (lastSample != null) _state.update {
            it.copy(sampleFile = lastSample, sampleName = lastSample.name, status = "محفوظ ریکارڈنگ موجود ہے")
        }
    }

    fun startRecording() {
        if (_state.value.isBusy || _state.value.isRecording) return
        val output = File(getApplication<Application>().filesDir, "voice_samples/recorded_${System.currentTimeMillis()}.partial")
        runCatching {
            recorder.start(viewModelScope, output)
            activeRecordingFile = output
            _state.update { it.copy(isRecording = true, recordingSeconds = 0, status = "ریکارڈنگ جاری ہے — زیادہ سے زیادہ 15 سیکنڈ") }
            recordingTimer = viewModelScope.launch {
                for (second in 1..15) {
                    kotlinx.coroutines.delay(1000)
                    if (!_state.value.isRecording) return@launch
                    _state.update { it.copy(recordingSeconds = second) }
                }
                stopRecording()
            }
        }.onFailure { error ->
            activeRecordingFile = null
            _state.update { it.copy(status = error.message ?: "Unable to start recording") }
        }
    }

    fun stopRecording() {
        if (!_state.value.isRecording || _state.value.isBusy) return
        recordingTimer?.cancel()
        _state.update { it.copy(isBusy = true, status = "ریکارڈنگ محفوظ ہو رہی ہے…") }
        viewModelScope.launch {
            try {
                recorder.stop()
                val partial = activeRecordingFile
                val ready = partial?.takeIf { it.isFile && it.length() > 44L }?.let {
                    val target = File(it.parentFile, it.nameWithoutExtension + ".wav")
                    if (it.renameTo(target)) target else null
                }
                _state.update {
                    it.copy(isRecording = false, isBusy = false,
                        sampleFile = ready ?: it.sampleFile, sampleName = ready?.name ?: it.sampleName,
                        status = if (ready != null) "آپ کی ریکارڈنگ محفوظ ہے" else "ریکارڈنگ مکمل نہیں ہوئی؛ دوبارہ کوشش کریں")
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.update { it.copy(isRecording = false, isBusy = false, status = "ریکارڈنگ محفوظ نہیں ہوئی: ${error.message}") }
            } finally {
                activeRecordingFile?.delete()
                activeRecordingFile = null
            }
        }
    }

    fun onMicrophonePermissionDenied() {
        _state.update { it.copy(status = "مائیکروفون کی اجازت نہیں ملی") }
    }

    fun importAudio(uri: Uri) {
        if (_state.value.isBusy || _state.value.isRecording) return
        val requestId = ++generation
        _state.update { it.copy(isBusy = true, status = "آواز امپورٹ ہو رہی ہے…") }
        previewJob = viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { imports.copyIntoPrivateStorage(uri) }
                if (generation == requestId) _state.update {
                    it.copy(sampleFile = file, sampleName = file.name, isBusy = false, status = "امپورٹ کیا ہوا نمونہ محفوظ ہے")
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (generation == requestId) _state.update { it.copy(isBusy = false, status = "امپورٹ نہیں ہوا: ${error.message}") }
            }
        }
    }

    fun setSpeed(speed: Float) { if (!_state.value.isBusy) _state.update { it.copy(speed = speed.coerceIn(0.85f, 1.4f)) } }

    fun setGender(gender: VoiceGender) { if (!_state.value.isBusy && !_state.value.isRecording) _state.update { it.copy(gender = gender) } }

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
                    _state.update { state -> state.copy(isBusy = false, status = "ریکارڈنگ سننا مکمل ہوا") }
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    samplePlayer = null
                    _state.update { state -> state.copy(isBusy = false, status = "ریکارڈنگ نہیں چل سکی") }
                    true
                }
                prepare()
                start()
            }
            _state.update { it.copy(isBusy = true, status = "آپ کی ریکارڈنگ چل رہی ہے") }
        }.onFailure {
            samplePlayer?.release()
            samplePlayer = null
            _state.update { state -> state.copy(isBusy = false, status = "Unable to play sample: ${it.message}") }
        }
    }

    fun playLastResult() {
        val audio = lastOutput ?: return
        if (_state.value.isBusy || _state.value.isRecording) return
        val requestId = ++generation
        previewJob = viewModelScope.launch {
            _state.update { it.copy(isBusy = true, status = "آخری تیار شدہ حصہ چل رہا ہے") }
            try {
                player.speed = _state.value.speed
                player.playAndWait(audio.pcm16, audio.sampleRateHz)
                if (generation == requestId) _state.update { it.copy(isBusy = false, status = "آواز مکمل ہوئی") }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (generation == requestId) _state.update { it.copy(isBusy = false, status = "Playback failed: ${error.message}") }
            }
        }
    }

    fun updateText(value: String) { if (!_state.value.isBusy && !_state.value.isRecording) _state.update { it.copy(text = value.take(20_000)) } }

    fun setLanguage(language: ReplyLanguage) {
        if (_state.value.isBusy || _state.value.isRecording) return
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
            _state.update { it.copy(isBusy = true, status = "بنیادی آواز تیار ہو رہی ہے…") }
            runCatching {
                val parts = SpeechTextPlanner.plan(snapshot.text, snapshot.language)
                player.speed = snapshot.speed
                baseVoice.withSession {
                    SpeechQueue.play(parts, prepare = { index, part ->
                        if (generation != requestId) throw CancellationException("روک دیا گیا")
                        if (index < 2) _state.update { it.copy(status = "Buffering speech ${index + 1}/${minOf(2, parts.size)}…") }
                        baseVoice.synthesize(part.text, part.language, snapshot.gender)
                    }, consume = { index, audio ->
                        if (generation != requestId) throw CancellationException("روک دیا گیا")
                        lastOutput = audio
                        _state.update { it.copy(resultLabel = "Base Voice — ${snapshot.gender.name}", status = "Reading ${index + 1}/${parts.size} — ${snapshot.gender.name}, ${snapshot.speed}x") }
                        player.playAndWait(audio.pcm16, audio.sampleRateHz)
                    })
                }
            }
                .onSuccess {
                    if (generation == requestId) {
                        _state.update { it.copy(isBusy = false, status = "متن پڑھنا مکمل ہوا") }
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
            _state.update { it.copy(isBusy = true, status = "انگلش کلون کا تجربہ تیار ہو رہا ہے…") }
            try {
                player.speed = snapshot.speed
                baseVoice.withSession {
                    SpeechQueue.play(parts, prepare = { index, part ->
                        if (generation != requestId) throw CancellationException("روک دیا گیا")
                        if (index < 2) _state.update { it.copy(status = "Buffering experimental clone — sample: ${sample.name}") }
                        baseVoice.synthesize(part.text, part.language, reference = sample)
                    }, consume = { index, audio ->
                        if (generation != requestId) throw CancellationException("روک دیا گیا")
                        lastOutput = audio
                        _state.update { it.copy(resultLabel = "Reference test — ${sample.name}", status = "Reference-conditioned test ${index + 1}/${parts.size} — verify similarity") }
                        player.playAndWait(audio.pcm16, audio.sampleRateHz)
                    })
                }
                if (generation == requestId) _state.update { it.copy(isBusy = false, status = "آواز کا تجربہ مکمل — ریکارڈنگ اور نتیجہ سن کر مشابہت چیک کریں") }
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
            if (!_state.value.isBusy) stopRecording()
            return
        }
        player.stop()
        _state.update { it.copy(isBusy = false, status = "روک دیا گیا") }
    }

    override fun onCleared() {
        generation++
        samplePlayer?.release()
        samplePlayer = null
        baseVoice.stop()
        recordingTimer?.cancel()
        CoroutineScope(Dispatchers.IO).launch { recorder.stop(); baseVoice.release() }
        player.stop()
        super.onCleared()
    }
}
