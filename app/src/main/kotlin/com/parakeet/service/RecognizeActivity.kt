package com.parakeet.service

import android.app.Activity
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.io.ByteArrayOutputStream

class RecognizeActivity : Activity() {

    companion object {
        private const val TAG = "ParakeetRecognize"
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
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "RecognizeActivity started")

        if (!ModelManager.loadModel(this)) {
            Log.e(TAG, "Model not loaded")
            returnResult("")
            return
        }

        startRecording()
    }

    private fun startRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size")
            returnResult("")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized")
            returnResult("")
            return
        }

        audioBuffer = ByteArrayOutputStream()
        val maxBytes = SAMPLE_RATE * 2 * MAX_DURATION_SECS

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = Thread {
            val readBuffer = ShortArray(bufferSize / 2)
            var speechOnsetCounter = 0
            var inSpeech = false
            var silenceCounter = 0
            var speechDetected = false

            while (isRecording) {
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
                                isRecording = false
                            }
                        }
                    }

                    val buf = audioBuffer
                    if (buf != null && buf.size() < maxBytes) {
                        buf.write(byteBuffer)
                    } else {
                        isRecording = false
                    }
                } else if (readCount < 0) {
                    break
                }
            }

            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (_: Exception) {}
            audioRecord = null

            val audioData = audioBuffer?.toByteArray() ?: ByteArray(0)
            audioBuffer = null

            if (!speechDetected || audioData.isEmpty()) {
                runOnUiThread { returnResult("") }
                return@Thread
            }

            try {
                val text = NativeLib.transcribe(audioData).trim()
                Log.i(TAG, "Transcription: \"$text\"")
                runOnUiThread { returnResult(text) }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription error", e)
                runOnUiThread { returnResult("") }
            }
        }.also { it.name = "parakeet-recognize"; it.start() }
    }

    private fun returnResult(text: String) {
        val intent = Intent().apply {
            putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf(text))
            putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, floatArrayOf(if (text.isEmpty()) 0.0f else 0.9f))
        }
        setResult(if (text.isEmpty()) RESULT_CANCELED else RESULT_OK, intent)
        finish()
    }

    override fun onDestroy() {
        isRecording = false
        recordingThread?.join(2000)
        recordingThread?.interrupt()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
