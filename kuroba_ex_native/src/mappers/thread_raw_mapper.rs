pub mod mapper {
  use new_post_parser_lib::{ThreadRaw, PostRaw};
  use jni::{JNIEnv, errors};
  use jni::objects::{JObject};
  use crate::helpers::{java_string_field_to_rust_string, format_post_parsing_object_signature_pref};

  pub fn from_java_object(env: &JNIEnv, threads_to_parse: JObject) -> errors::Result<ThreadRaw> {
    let posts_to_parse_list_field =  env.get_field(
      threads_to_parse,
      "postToParseList",
      format_post_parsing_object_signature_pref("[", "PostToParse").as_str()
    )?;

    let posts_to_parse_list_object = posts_to_parse_list_field.l().unwrap().into_inner();
    let posts_to_parse_array_size = env.get_array_length(posts_to_parse_list_object)?;

    if posts_to_parse_array_size <= 0 {
      return Result::Ok(ThreadRaw { posts: Vec::new() });
    }

    let mut posts = Vec::<PostRaw>::with_capacity(posts_to_parse_array_size as usize);

    for index in 0..posts_to_parse_array_size {
      let post_raw_object = env.get_object_array_element(posts_to_parse_list_object, index)?;
      posts.push(post_raw_object_to_post_raw(env, post_raw_object)?);
    }

    return Result::Ok(ThreadRaw { posts } )
  }

  fn post_raw_object_to_post_raw(env: &JNIEnv, post_raw_object: JObject) -> errors::Result<PostRaw> {
    let post_id = env.get_field(post_raw_object, "postId", "J")?.j()? as u64;
    let post_sub_id = env.get_field(post_raw_object, "postSubId", "J")?.j()? as u64;
    let comment = java_string_field_to_rust_string(env, post_raw_object, "comment")?;

    return Result::Ok(
      PostRaw {
        post_id,
        post_sub_id,
        com: comment
      }
    )
  }

}