pub mod mapper {
  use jni::objects::{JObject, ReleaseMode};
  use jni::{JNIEnv, errors};
  use new_post_parser_lib::PostParserContext;
  use std::collections::HashSet;
  use crate::helpers::java_string_field_to_rust_string;

  pub fn from_java_object(env: &JNIEnv, post_parser_context: JObject) -> errors::Result<PostParserContext> {
    let thread_id_field = env.get_field(
      post_parser_context,
      "threadId",
      "J"
    )?;

    let post_parser_context = PostParserContext::new(
      java_string_field_to_rust_string(env, post_parser_context, "siteName")?.as_str(),
      java_string_field_to_rust_string(env, post_parser_context, "boardCode")?.as_str(),
      thread_id_field.j()? as u64,
      map_my_replies_in_thread_jarray(env, post_parser_context)?,
      map_thread_posts_jarray(env, post_parser_context)?
    );

    return errors::Result::Ok(post_parser_context);
  }

  fn map_thread_posts_jarray(env: &JNIEnv, post_parser_context: JObject) -> errors::Result<HashSet<u64>> {
    let thread_posts_jvalue = env.get_field(
      post_parser_context,
      "threadPosts",
      "[J"
    )?;

    let thread_posts_jarray = env.get_long_array_elements(
      thread_posts_jvalue.l().unwrap().into_inner(),
      ReleaseMode::NoCopyBack
    )?;

    let thread_posts_size = thread_posts_jarray.size()?;
    let mut thread_posts_set: HashSet<u64> = HashSet::new();

    unsafe {
      let mut ptr = thread_posts_jarray.as_ptr();

      for _ in 0..thread_posts_size {
        thread_posts_set.insert(*ptr as u64);
        ptr = ptr.offset(1)
      }
    }

    return errors::Result::Ok(thread_posts_set);
  }

  fn map_my_replies_in_thread_jarray(env: &JNIEnv, post_parser_context: JObject) -> errors::Result<HashSet<u64>> {
    let my_replies_in_thread_jvalue = env.get_field(
      post_parser_context,
      "myRepliesInThread",
      "[J"
    )?;

    let my_replies_in_thread_jarray = env.get_long_array_elements(
      my_replies_in_thread_jvalue.l().unwrap().into_inner(),
      ReleaseMode::NoCopyBack
    )?;

    let my_replies_in_thread_jarray_size = my_replies_in_thread_jarray.size()?;
    let mut my_replies_in_thread_set: HashSet<u64> = HashSet::new();

    unsafe {
      let mut ptr = my_replies_in_thread_jarray.as_ptr();

      for _ in 0..my_replies_in_thread_jarray_size {
        my_replies_in_thread_set.insert(*ptr as u64);
        ptr = ptr.offset(1)
      }
    }

    return errors::Result::Ok(my_replies_in_thread_set);
  }
}