use jni::objects::{JClass, JString};
use jni::JNIEnv;

#[no_mangle]
pub extern "system" fn Java_com_parakeet_service_NativeLib_greet<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass<'a>,
    input: JString<'a>,
) -> JString<'a> {
    let input_str: String = env.get_string(&input).unwrap().into();
    let response = format!("Hello from Rust! You said: {}", input_str);
    env.new_string(response).unwrap()
}
