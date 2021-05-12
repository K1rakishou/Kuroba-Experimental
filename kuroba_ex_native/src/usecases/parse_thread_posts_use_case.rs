pub mod usecases {
  use jni::{JNIEnv};
  use jni::objects::{JObject, JValue};
  use jni::sys::{jobject, jsize};
  use crate::mappers::{post_parser_context_mapper, thread_raw_mapper, post_parsed_mapper};
  use new_post_parser_lib::{PostParser};
  use crate::helpers::{format_post_parsing_object_signature, format_post_parsing_object_signature_pref};

  pub fn parse_thread_posts_use_case(
    env: &JNIEnv,
    post_parser_context: JObject,
    threads_to_parse: JObject
  ) -> jobject {
    let post_parser_context = post_parser_context_mapper::mapper::from_java_object(
      &env,
      post_parser_context
    ).expect("Failed to map post_parser_context");

    let thread_raw = thread_raw_mapper::mapper::from_java_object(
      &env,
      threads_to_parse
    ).expect("Failed to map thread_raw");

    let post_parser = PostParser::new(&post_parser_context);

    let thread_parsed_jclass = env.find_class(
      format_post_parsing_object_signature("ThreadParsed").as_str()
    ).expect("Failed to find class ThreadParsed");

    let thread_parsed_jobject = env.new_object(thread_parsed_jclass, "()V", &[])
      .expect("Failed to instantiate ThreadParsed");

    let post_parsed_jclass = env.find_class(
      format_post_parsing_object_signature("PostParsed").as_str()
    ).expect("Failed to find class PostParsed");

    let post_parsed_list = env.new_object_array(
      thread_raw.posts.len() as jsize,
      post_parsed_jclass,
      JObject::null()
    ).expect("Failed to allocate array for PostParsed");

    for (index, post_raw) in thread_raw.posts.iter().enumerate() {
      let parsed_post = post_parser.parse_post(&post_raw);

      let post_parsed_jobject = post_parsed_mapper::mapper::map_to_post_parsed(
        &env,
        &parsed_post
      ).expect("post_parsed_mapper::mapper::map_to_post_parsed error");

      env.set_object_array_element(
        post_parsed_list,
        index as jsize,
        post_parsed_jobject
      ).expect("Failed to add post_parsed_jobject to post_parsed_list");
    }

    env.set_field(
      thread_parsed_jobject,
      "postParsedList",
      format_post_parsing_object_signature_pref("[", "PostParsed"),
      JValue::Object(JObject::from(post_parsed_list))
    ).expect("Failed to set field postParsedList of thread_parsed_jobject");

    return thread_parsed_jobject.into_inner();
  }
}