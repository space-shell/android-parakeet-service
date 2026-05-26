package com.parakeet.service

import android.content.Context
import android.util.Log
import java.io.File

object ModelManager {

    private const val TAG = "ParakeetModel"
    private const val MODEL_DIR_NAME = "parakeet-model"

    data class ModelFile(val name: String, val minSize: Long)

    val MODEL_FILES = listOf(
        ModelFile("encoder-model.int8.onnx", 100_000_000L),
        ModelFile("decoder_joint-model.int8.onnx", 5_000_000L),
        ModelFile("nemo128.onnx", 10_000L),
        ModelFile("vocab.txt", 10_000L),
    )

    fun getModelDir(context: Context): File {
        return File(context.filesDir, MODEL_DIR_NAME)
    }

    fun isModelExtracted(context: Context): Boolean {
        val dir = getModelDir(context)
        if (!dir.exists()) return false
        return MODEL_FILES.all { mf ->
            val file = File(dir, mf.name)
            file.exists() && file.length() >= mf.minSize
        }
    }

    fun extractModelAssets(context: Context): Boolean {
        val dir = getModelDir(context)
        if (!dir.exists()) dir.mkdirs()

        for (mf in MODEL_FILES) {
            val target = File(dir, mf.name)
            if (target.exists() && target.length() >= mf.minSize) {
                Log.d(TAG, "SKIP (valid): ${mf.name} (${target.length()} bytes)")
                continue
            }

            if (target.exists()) {
                Log.w(TAG, "Corrupted/incomplete: ${mf.name} (${target.length()} < ${mf.minSize}), re-copying")
                target.delete()
            }

            try {
                context.assets.open(mf.name).use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy ${mf.name}", e)
                target.delete()
                return false
            }

            if (target.length() < mf.minSize) {
                Log.e(TAG, "Copied file too small: ${mf.name} (${target.length()} < ${mf.minSize})")
                target.delete()
                return false
            }

            Log.d(TAG, "Copied: ${mf.name} (${target.length()} bytes)")
        }
        return true
    }

    fun loadModel(context: Context): Boolean {
        if (!isModelExtracted(context)) {
            val extracted = extractModelAssets(context)
            if (!extracted) {
                Log.e(TAG, "Model asset extraction failed")
                return false
            }
        }
        val dir = getModelDir(context)
        return NativeLib.loadModel(dir.absolutePath)
    }
}
