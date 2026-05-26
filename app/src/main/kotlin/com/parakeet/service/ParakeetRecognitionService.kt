package com.parakeet.service

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class ParakeetRecognitionService : RecognitionService() {

    companion object {
        private const val TAG = "ParakeetRecognition"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_DURATION_SECS = 60

        private const val VAD_THRESHOLD_RMS = 500.0f
        private const val VAD_ONSET_FRAMES = 3
        private const val VAD_HANGOVER_FRAMES = 50
    }

    private var audioRecord: AudioRecord? = null
    private var audioBuffer: ByteArrayOutputStream? = null
    private var recordingThread: Thread? = null
    private var inferenceThread: Thread? = null
    private val isRecording = AtomicBoolean(false)
    private val isProcessing = AtomicBoolean(false)
    private var modelLoaded = false
    private var currentCallback: Callback? = null

    override fun onCreate() {
        super.onCreate()
        Thread {
            val success = ModelManager.loadModel(this)
            synchronized(this) {
                modelLoaded = success
            }
        }.start()
    }

    override fun onStartListening(intent: android.content.Intent, callback: Callback) {
        Log.d(TAG, "onStartListening")
        currentCallback = callback

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            return
        }

        synchronized(this) {
            if (!modelLoaded) {
                Log.e(TAG, "Model not loaded")
                callback.error(SpeechRecognizer.ERROR_CLIENT)
                return
            }
        }

        if (!isProcessing.compareAndSet(false, true)) {
            Log.w(TAG, "Recognition already in progress, rejecting")
            callback.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size")
            callback.error(SpeechRecognizer.ERROR_AUDIO)
            isProcessing.set(false)
            return
        }

        try {
            audioRecord = AudioRecord(
                android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized")
                callback.error(SpeechRecognizer.ERROR_AUDIO)
                releaseAudioRecord()
                isProcessing.set(false)
                return
            }

            audioBuffer = ByteArrayOutputStream()
            val maxBytes = SAMPLE_RATE * 2 * MAX_DURATION_SECS

            audioRecord?.startRecording()
            isRecording.set(true)
            callback.beginningOfSpeech()

            recordingThread = Thread {
                val readBuffer = ShortArray(bufferSize / 2)
                var speechOnsetCounter = 0
                var inSpeech = false
                var silenceCounter = 0
                var speechDetected = false

                while (isRecording.get()) {
                    val readCount = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                    if (readCount > 0) {
                        val byteBuffer = ByteArray(readCount * 2)
                        var sumSquares = 0.0
                        for (i in 0 until readCount) {
                            val sample = readBuffer[i]
                            byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = (sample.toInt() shr 8).toByte()
                            sumSquares += sample.toDouble() * sample.toDouble()
                        }
                        val rms = Math.sqrt(sumSquares / readCount).toFloat()

                        val isSpeechFrame = rms > VAD_THRESHOLD_RMS

                        if (!inSpeech) {
                            if (isSpeechFrame) {
                                speechOnsetCounter++
                                if (speechOnsetCounter >= VAD_ONSET_FRAMES) {
                                    inSpeech = true
                                    speechDetected = true
                                    silenceCounter = 0
                                    Log.d(TAG, "Speech onset detected (rms=$rms)")
                                }
                            } else {
                                speechOnsetCounter = 0
                            }
                        } else {
                            if (isSpeechFrame) {
                                silenceCounter = 0
                            } else {
                                silenceCounter++
                                if (silenceCounter >= VAD_HANGOVER_FRAMES) {
                                    inSpeech = false
                                    Log.d(TAG, "Silence detected after speech (${silenceCounter} frames), auto-stopping")
                                    isRecording.set(false)
                                }
                            }
                        }

                        synchronized(this) {
                            val buf = audioBuffer
                            if (buf != null && buf.size() < maxBytes) {
                                buf.write(byteBuffer)
                            } else {
                                Log.w(TAG, "Audio buffer full, stopping recording")
                                isRecording.set(false)
                            }
                        }
                    } else if (readCount < 0) {
                        Log.e(TAG, "AudioRecord.read error: $readCount")
                        break
                    }
                }

                if (speechDetected) {
                    Log.d(TAG, "Recording finished with speech captured, triggering transcription")
                    Handler(Looper.getMainLooper()).post { performTranscription() }
                } else {
                    Log.d(TAG, "No speech detected during recording")
                    Handler(Looper.getMainLooper()).post {
                        currentCallback?.let { returnEmptyResult(it) }
                        releaseAudioRecord()
                        isProcessing.set(false)
                    }
                }
            }.also { it.name = "parakeet-audio-capture"; it.start() }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during audio recording", e)
            callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            releaseAudioRecord()
            isProcessing.set(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recording", e)
            callback.error(SpeechRecognizer.ERROR_AUDIO)
            releaseAudioRecord()
            isProcessing.set(false)
        }
    }

    override fun onStopListening(callback: Callback) {
        Log.d(TAG, "onStopListening (manual)")
        isRecording.set(false)
        recordingThread?.join(3000)
        if (recordingThread?.isAlive == true) {
            Log.w(TAG, "Recording thread did not finish in time")
            recordingThread?.interrupt()
        }
        recordingThread = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}

        performTranscription()
    }

    private fun performTranscription() {
        val callback = currentCallback
        if (callback == null) {
            Log.e(TAG, "No callback available for transcription")
            releaseAudioRecord()
            isProcessing.set(false)
            return
        }

        val audioData: ByteArray
        synchronized(this) {
            audioData = audioBuffer?.toByteArray() ?: ByteArray(0)
            audioBuffer = null
        }

        Log.d(TAG, "Captured ${audioData.size} bytes of audio (${audioData.size / 2} samples)")

        if (audioData.isEmpty()) {
            Log.d(TAG, "No audio captured, returning empty result")
            returnEmptyResult(callback)
            releaseAudioRecord()
            isProcessing.set(false)
            return
        }

        inferenceThread = Thread {
            try {
                val transcription = NativeLib.transcribe(audioData)
                val text = transcription.trim()

                if (text.isEmpty()) {
                    Log.d(TAG, "Transcription returned empty text")
                    returnEmptyResult(callback)
                } else {
                    Log.i(TAG, "Transcription result: \"$text\"")
                    val bundle = Bundle().apply {
                        putStringArrayList(
                            RecognizerIntent.EXTRA_RESULTS,
                            arrayListOf(text)
                        )
                        putFloatArray(
                            RecognizerIntent.EXTRA_CONFIDENCE_SCORES,
                            floatArrayOf(0.9f)
                        )
                    }
                    callback.results(bundle)
                }
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "JNI library not loaded", e)
                callback.error(SpeechRecognizer.ERROR_CLIENT)
            } catch (e: Exception) {
                Log.e(TAG, "Transcription error", e)
                callback.error(SpeechRecognizer.ERROR_CLIENT)
            } finally {
                releaseAudioRecord()
                isProcessing.set(false)
            }
        }.also { it.name = "parakeet-inference"; it.start() }
    }

    override fun onCancel(callback: Callback) {
        Log.d(TAG, "onCancel")
        isRecording.set(false)
        recordingThread?.join(1000)
        recordingThread?.interrupt()
        recordingThread = null
        releaseAudioRecord()
        isProcessing.set(false)
        currentCallback = null
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        isRecording.set(false)

        recordingThread?.join(2000)
        recordingThread?.interrupt()
        recordingThread = null

        inferenceThread?.join(5000)
        inferenceThread?.interrupt()
        inferenceThread = null

        releaseAudioRecord()
        NativeLib.destroy()
        currentCallback = null
        super.onDestroy()
    }

    private fun releaseAudioRecord() {
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        audioBuffer = null
    }

    private fun returnEmptyResult(callback: Callback) {
        val bundle = Bundle().apply {
            putStringArrayList(
                RecognizerIntent.EXTRA_RESULTS,
                arrayListOf("")
            )
            putFloatArray(
                RecognizerIntent.EXTRA_CONFIDENCE_SCORES,
                floatArrayOf(0.0f)
            )
        }
        callback.results(bundle)
    }
}
