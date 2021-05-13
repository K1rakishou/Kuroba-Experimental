use jni::{JNIEnv, errors};
use jni::objects::{JObject, JString};
use std::ffi::{CStr, CString};
use std::thread;
use log::error;

pub fn unwrap_exc_or<T>(env: &JNIEnv, res: thread::Result<T>, error_val: T) -> T {
  return match res {
    // No JNI error
    Ok(val) => val,
    // JNI error
    Err(jni_error) => {
      // Do nothing if there is a pending Java-exception that will be thrown
      // automatically by the JVM when the native method returns.
      if !env.exception_check().unwrap() {
        // Throw a Java exception manually in case of an internal error.
        throw(env, format!("{:?}", &jni_error).as_str())
      }

      error_val
    }
  }
}

fn throw(env: &JNIEnv, description: &str) {
  // We cannot throw exception from this function, so errors should be written in log instead.
  let exception = match env.find_class("java/lang/RuntimeException") {
    Ok(val) => val,
    Err(e) => {
      error!("Unable to find 'RuntimeException' class: {}", e.to_string());
      return;
    }
  };

  if let Err(e) = env.throw_new(exception, description) {
    error!("Unable to find 'RuntimeException' class: {}", e.to_string());
  }
}

pub fn java_string_field_to_rust_string(
  env: &JNIEnv,
  object_holder: JObject,
  field_name: &str
) -> errors::Result<String> {
  let comment_field = env.get_field(object_holder, field_name, "Ljava/lang/String;")?.l()?;
  let result_string: String;

  unsafe {
    let java_string = env.get_string(JString::from(comment_field))
      .unwrap()
      .as_ptr();

    let c_str = CStr::from_ptr(java_string);
    let c_string = CString::from(c_str);

    result_string = String::from(c_string.to_str().unwrap());
  }

  return Result::Ok(result_string)
}

pub fn format_post_parsing_object_signature(class_name: &str) -> String {
  return format!("com/github/k1rakishou/chan/core/lib/data/post_parsing/{}", class_name)
}

pub fn format_post_parsing_object_signature_pref(preffix: &str, class_name: &str) -> String {
  return format!("{}L{};", preffix, format_post_parsing_object_signature(class_name).as_str());
}

pub fn format_spannables_object_signature(class_name: &str) -> String {
  return format!("com/github/k1rakishou/chan/core/lib/data/post_parsing/spannable/{}", class_name)
}

pub fn format_spannables_object_signature_pref(preffix: &str, class_name: &str) -> String {
  return format!("{}L{};", preffix, format_spannables_object_signature(class_name).as_str());
}

pub fn format_descriptors_object_signature(class_name: &str) -> String {
  return format!("com/github/k1rakishou/chan/core/lib/data/descriptor/{}", class_name)
}

pub fn format_descriptors_object_signature_pref(preffix: &str, class_name: &str) -> String {
  return format!("{}L{};", preffix, format_descriptors_object_signature(class_name).as_str());
}