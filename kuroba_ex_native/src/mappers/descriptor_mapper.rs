pub mod mapper {
  use new_post_parser_lib::PostDescriptor;
  use jni::{JNIEnv, errors};
  use jni::sys::{jobject};
  use crate::helpers::format_descriptors_object_signature;
  use jni::objects::{JObject, JValue};

  pub fn map_post_descriptor_to_jobject(env: &JNIEnv, post_descriptor: &PostDescriptor) -> errors::Result<jobject> {
    let site_name = post_descriptor.site_name();
    let board_code = post_descriptor.board_code();
    let thread_no = post_descriptor.thread_no();
    let post_no = post_descriptor.post_no;
    let post_sub_no = post_descriptor.post_sub_no;

    let site_descriptor_native_sign = format_descriptors_object_signature("SiteDescriptorNative");
    let board_descriptor_native_sign = format_descriptors_object_signature("BoardDescriptorNative");
    let thread_descriptor_native_sign = format_descriptors_object_signature("ThreadDescriptorNative");
    let post_descriptor_native_sign = format_descriptors_object_signature("PostDescriptorNative");

    let site_descriptor_native_jobject = env.new_object(
      env.find_class(&site_descriptor_native_sign)?,
      "(Ljava/lang/String;)V",
      &[JValue::Object(JObject::from(env.new_string(site_name)?.into_inner()))],
    ).expect("Failed to instantiate SiteDescriptorNative");

    let board_descriptor_native_jobject = env.new_object(
      env.find_class(&board_descriptor_native_sign)?,
      format!("(L{};Ljava/lang/String;)V", &site_descriptor_native_sign),
      &[
        JValue::Object(site_descriptor_native_jobject),
        JValue::Object(JObject::from(env.new_string(board_code)?.into_inner()))
      ],
    ).expect("Failed to instantiate BoardDescriptorNative");

    let thread_descriptor_native_jobject = env.new_object(
      env.find_class(&thread_descriptor_native_sign)?,
      format!("(L{};J)V", &board_descriptor_native_sign),
      &[
        JValue::Object(board_descriptor_native_jobject),
        JValue::Long(thread_no as i64)
      ],
    ).expect("Failed to instantiate ThreadDescriptorNative");

    let post_descriptor_jobject =  env.new_object(
      env.find_class(&post_descriptor_native_sign)?,
      format!("(L{};JJ)V", &thread_descriptor_native_sign),
      &[
        JValue::Object(thread_descriptor_native_jobject),
        JValue::Long(post_no as i64),
        JValue::Long(post_sub_no as i64)
      ],
    ).expect("Failed to instantiate PostDescriptorNative");

    return Result::Ok(post_descriptor_jobject.into_inner());
  }
}