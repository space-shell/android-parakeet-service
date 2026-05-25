use std::sync::Mutex;

use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jboolean, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

use transcribe_rs::onnx::parakeet::ParakeetModel;
use transcribe_rs::onnx::Quantization;
use transcribe_rs::SpeechModel;

static MODEL: Mutex<Option<ParakeetModel>> = Mutex::new(None);

#[no_mangle]
pub extern "system" fn Java_com_parakeet_service_NativeLib_loadModel<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass<'a>,
    model_dir: JString<'a>,
) -> jboolean {
    let dir: String = match env.get_string(&model_dir) {
        Ok(s) => s.into(),
        Err(_) => return JNI_FALSE,
    };

    log::info!("Loading Parakeet model from: {}", dir);

    let model = match ParakeetModel::load(std::path::Path::new(&dir), &Quantization::Int8) {
        Ok(m) => m,
        Err(e) => {
            log::error!("Failed to load model: {}", e);
            return JNI_FALSE;
        }
    };

    match MODEL.lock() {
        Ok(mut guard) => {
            *guard = Some(model);
            log::info!("Model loaded successfully");
            JNI_TRUE
        }
        Err(_) => {
            log::error!("Failed to acquire model lock");
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_parakeet_service_NativeLib_transcribe<'a>(
    env: JNIEnv<'a>,
    _class: JClass<'a>,
    pcm_audio: JByteArray<'a>,
) -> JString<'a> {
    let audio_bytes = match env.convert_byte_array(&pcm_audio) {
        Ok(bytes) => bytes,
        Err(_) => {
            let _ = env.new_string("");
            return env.new_string("").unwrap();
        }
    };

    let samples = pcm_i16_to_f32(&audio_bytes);

    log::info!("Received {} PCM bytes ({} f32 samples)", audio_bytes.len(), samples.len());

    let text = match MODEL.lock() {
        Ok(mut guard) => match guard.as_mut() {
            Some(model) => match model.transcribe(&samples, &transcribe_rs::TranscribeOptions::default()) {
                Ok(result) => {
                    log::info!("Transcription: {}", result.text);
                    result.text
                }
                Err(e) => {
                    log::error!("Transcription failed: {}", e);
                    String::new()
                }
            },
            None => {
                log::error!("Model not loaded");
                String::new()
            }
        },
        Err(_) => {
            log::error!("Failed to acquire model lock");
            String::new()
        }
    };

    env.new_string(&text).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_parakeet_service_NativeLib_destroy<'a>(
    _env: JNIEnv<'a>,
    _class: JClass<'a>,
) {
    if let Ok(mut guard) = MODEL.lock() {
        *guard = None;
        log::info!("Model destroyed");
    }
}

fn pcm_i16_to_f32(bytes: &[u8]) -> Vec<f32> {
    bytes
        .chunks_exact(2)
        .map(|chunk| {
            let sample = i16::from_le_bytes([chunk[0], chunk[1]]);
            sample as f32 / 32768.0
        })
        .collect()
}
