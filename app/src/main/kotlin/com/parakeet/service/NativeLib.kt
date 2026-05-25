package com.parakeet.service

object NativeLib {
    init {
        System.loadLibrary("parakeet_jni")
    }

    external fun loadModel(modelDir: String): Boolean
    external fun transcribe(pcmAudio: ByteArray): String
    external fun destroy()
}
