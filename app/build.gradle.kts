plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.parakeet.service"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.parakeet.service"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

val rustReleaseDir = layout.buildDirectory.dir("rustRelease")
val jniLibsDir = file("src/main/jniLibs/arm64-v8a")
val assetsDir = file("src/main/assets")

val modelBaseUrl = "https://huggingface.co/istupakov/parakeet-tdt-0.6b-v3-onnx/resolve/main"
val modelFiles = listOf(
    "encoder-model.int8.onnx",
    "decoder_joint-model.int8.onnx",
    "nemo128.onnx",
    "vocab.txt",
)

val downloadModel by tasks.registering {
    description = "Download Parakeet TDT int8 ONNX model from HuggingFace"
    outputs.files(modelFiles.map { assetsDir.resolve(it) })
    doLast {
        assetsDir.mkdirs()
        modelFiles.forEach { filename ->
            val target = assetsDir.resolve(filename)
            if (target.exists()) {
                logger.lifecycle("  SKIP (exists): $filename")
                return@forEach
            }
            val url = "$modelBaseUrl/$filename"
            logger.lifecycle("  DOWNLOAD: $filename from $url")
            ant.withGroovyBuilder {
                "get"("src" to url, "dest" to target)
            }
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(downloadModel)
}

val buildRustRelease by tasks.registering(Exec::class) {
    description = "Cross-compile Rust cdylib for aarch64-linux-android"
    workingDir = rootProject.file(".")
    val ndkHome = System.getenv("ANDROID_NDK_HOME") ?: System.getenv("NDK_HOME") ?: ""
    environment("ANDROID_NDK_HOME", ndkHome)
    environment("NDK_HOME", ndkHome)
    val toolchain = if (System.getProperty("os.name").lowercase().contains("linux")) {
        "linux-x86_64"
    } else {
        "darwin-x86_64"
    }
    val ar = if (ndkHome.isNotEmpty()) {
        "$ndkHome/toolchains/llvm/prebuilt/$toolchain/bin/llvm-ar"
    } else { "ar" }
    val linker = if (ndkHome.isNotEmpty()) {
        "$ndkHome/toolchains/llvm/prebuilt/$toolchain/bin/aarch64-linux-android33-clang"
    } else { "gcc" }
    commandLine(
        "cargo", "ndk", "-t", "arm64-v8a",
        "--platform", "31",
        "build", "--release"
    )
    environment("AR_aarch64_linux_android", ar)
    environment("CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER", linker)
    inputs.file(rootProject.file("Cargo.toml"))
    inputs.file(rootProject.file("Cargo.lock"))
    inputs.dir(rootProject.file("src"))
    outputs.dir(rootProject.file("target/aarch64-linux-android/release"))
}

val copyRustSo by tasks.registering(Copy::class) {
    description = "Copy built .so into jniLibs"
    dependsOn(buildRustRelease)
    from(rootProject.file("target/aarch64-linux-android/release/libparakeet_jni.so"))
    into(jniLibsDir)
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }.configureEach {
    dependsOn(copyRustSo)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(copyRustSo)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
