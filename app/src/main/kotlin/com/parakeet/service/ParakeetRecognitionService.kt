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
import java.io.ByteArrayOutputStream
import java.io.File

class ParakeetRecognitionService : RecognitionService() {

    companion object {
        private const val TAG = "ParakeetRecognition"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_DURATION_SECS = 60
    }

    private var audioRecord: AudioRecord? = null
    private var audioBuffer: ByteArrayOutputStream? = null
    private var recordingThread: Thread? = null
    @Volatile
    private var isRecording = false
    private var currentCallback: Callback? = null
    private var modelLoaded = false

    override fun onCreate() {
        super.onCreate()
        loadModel()
    }

    private fun loadModel() {
        try {
            val modelDir = File(filesDir, "parakeet-model")
            if (!modelDir.exists()) {
                modelDir.mkdirs()
                copyModelAssets(modelDir)
            }
            modelLoaded = NativeLib.loadModel(modelDir.absolutePath)
            if (!modelLoaded) {
                Log.e(TAG, "Model load failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
        }
    }

    private fun copyModelAssets(targetDir: File) {
        val modelFiles = listOf(
            "encoder-model.int8.onnx",
            "decoder_joint-model.int8.onnx",
            "nemo128.onnx",
            "vocab.txt"
        )
        for (filename in modelFiles) {
            assets.open(filename).use { input ->
                File(targetDir, filename).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Copied model asset: $filename")
        }
    }

    override fun onStartListening(intent: android.content.Intent, callback: Callback) {
        Log.d(TAG, "onStartListening")
        currentCallback = callback

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            return
        }

        if (!modelLoaded) {
            Log.e(TAG, "Model not loaded")
            callback.error(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size")
            callback.error(SpeechRecognizer.ERROR_AUDIO)
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
                return
            }

            audioBuffer = ByteArrayOutputStream()
            val maxBytes = SAMPLE_RATE * 2 * MAX_DURATION_SECS

            audioRecord?.startRecording()
            isRecording = true
            callback.beginningOfSpeech()

            recordingThread = Thread {
                val readBuffer = ShortArray(bufferSize / 2)
                while (isRecording) {
                    val readCount = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                    if (readCount > 0) {
                        val byteBuffer = ByteArray(readCount * 2)
                        for (i in 0 until readCount) {
                            val sample = readBuffer[i]
                            byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = (sample.toInt() shr 8).toByte()
                        }
                        synchronized(this) {
                            val buf = audioBuffer
                            if (buf != null && buf.size() < maxBytes) {
                                buf.write(byteBuffer)
                            }
                        }
                    } else {
                        break
                    }
                }
            }.also { it.start() }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during audio recording", e)
            callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            releaseAudioRecord()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recording", e)
            callback.error(SpeechRecognizer.ERROR_AUDIO)
            releaseAudioRecord()
        }
    }

    override fun onStopListening(callback: Callback) {
        Log.d(TAG, "onStopListening")
        isRecording = false
        recordingThread?.join(3000)
        recordingThread = null

        audioRecord?.stop()

        val audioData: ByteArray
        synchronized(this) {
            audioData = audioBuffer?.toByteArray() ?: ByteArray(0)
            audioBuffer = null
        }

        Log.d(TAG, "Captured ${audioData.size} bytes of audio")

        if (audioData.isEmpty()) {
            returnEmptyResult(callback)
            releaseAudioRecord()
            return
        }

        Thread {
            try {
                val transcription = NativeLib.transcribe(audioData)
                val text = transcription.trim()

                if (text.isEmpty()) {
                    returnEmptyResult(callback)
                } else {
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
            } catch (e: Exception) {
                Log.e(TAG, "Transcription error", e)
                callback.error(SpeechRecognizer.ERROR_CLIENT)
            }
            releaseAudioRecord()
        }.start()
    }

    override fun onCancel(callback: Callback) {
        Log.d(TAG, "onCancel")
        isRecording = false
        recordingThread?.join(1000)
        recordingThread = null
        releaseAudioRecord()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        isRecording = false
        recordingThread?.join(2000)
        recordingThread = null
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
