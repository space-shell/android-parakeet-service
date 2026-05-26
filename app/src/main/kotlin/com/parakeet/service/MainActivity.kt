package com.parakeet.service

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

enum class ModelStatus {
    LOADING, READY, FAILED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var modelStatus by remember { mutableStateOf(ModelStatus.LOADING) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Thread {
        val loaded = tryLoadModel(context)
        modelStatus = if (loaded) ModelStatus.READY else ModelStatus.FAILED
    }.start()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parakeet Voice Input") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ModelStatusCard(modelStatus)
            VoiceInputCard(context)
            TestTranscriptionCard(
                modelStatus = modelStatus,
                isTesting = isTesting,
                testResult = testResult,
                onTest = {
                    isTesting = true
                    testResult = null
                    Thread {
                        val result = runTestTranscription()
                        testResult = result
                        isTesting = false
                    }.start()
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Parakeet TDT 0.6B v3 • On-device • No network required",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ModelStatusCard(status: ModelStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                ModelStatus.LOADING -> MaterialTheme.colorScheme.secondaryContainer
                ModelStatus.READY -> MaterialTheme.colorScheme.tertiaryContainer
                ModelStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (status) {
                ModelStatus.LOADING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Loading model...", style = MaterialTheme.typography.bodyLarge)
                }
                ModelStatus.READY -> {
                    Text("●", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Model ready", style = MaterialTheme.typography.bodyLarge)
                }
                ModelStatus.FAILED -> {
                    Text("●", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Model failed to load", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun VoiceInputCard(context: android.content.Context) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Voice Input Service",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Enable this app as your voice input provider for system-wide speech recognition.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Voice Input Settings")
            }
        }
    }
}

@Composable
fun TestTranscriptionCard(
    modelStatus: ModelStatus,
    isTesting: Boolean,
    testResult: String?,
    onTest: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Test Transcription",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Generate a short test tone and run it through the model to verify the pipeline works.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = modelStatus == ModelStatus.READY && !isTesting,
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running...")
                    } else {
                        Text("Run Test")
                    }
                }
            }
            if (testResult != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Result:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = if (testResult.isEmpty()) "(empty)" else "\"$testResult\"",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

private fun tryLoadModel(context: android.content.Context): Boolean {
    return try {
        val modelDir = java.io.File(context.filesDir, "parakeet-model")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
            val modelFiles = listOf(
                "encoder-model.int8.onnx",
                "decoder_joint-model.int8.onnx",
                "nemo128.onnx",
                "vocab.txt"
            )
            for (filename in modelFiles) {
                context.assets.open(filename).use { input ->
                    java.io.File(modelDir, filename).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        NativeLib.loadModel(modelDir.absolutePath)
    } catch (e: Exception) {
        false
    }
}

private fun runTestTranscription(): String {
    val sampleRate = 16000
    val durationMs = 1000
    val numSamples = sampleRate * durationMs / 1000
    val frequency = 440.0
    val samples = ShortArray(numSamples)
    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        val amplitude = (Short.MAX_VALUE * 0.3 * Math.sin(2.0 * Math.PI * frequency * t)).toInt()
        samples[i] = amplitude.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    val bytes = ByteArray(numSamples * 2)
    for (i in 0 until numSamples) {
        bytes[i * 2] = (samples[i].toInt() and 0xFF).toByte()
        bytes[i * 2 + 1] = (samples[i].toInt() shr 8).toByte()
    }
    return NativeLib.transcribe(bytes)
}
