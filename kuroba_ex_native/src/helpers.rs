use jni::{JNIEnv, errors};
use jni::objects::{JObject, JString};
use std::ffi::{CStr, CString};

pub fn java_string_field_to_rust_string(
  env: &JNIEnv,
  object_holder: JObject,
  field_name: &str,
  field_signature: &str
) -> errors::Result<String> {
  let comment_field = env.get_field(object_holder, field_name, field_signature)?.l()?;
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