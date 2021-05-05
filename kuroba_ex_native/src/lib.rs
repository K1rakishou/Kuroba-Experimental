#![cfg(target_os = "android")]
#![allow(non_snake_case)]

mod post_parser_context_mapper;
mod thread_raw_mapper;
mod helpers;
mod post_comment_parsed_mapper;

use std::ffi::{CString, CStr};
use jni::JNIEnv;
use jni::objects::{JObject, JString, JClass, JValue};
use jni::sys::{jstring, jobject, jarray, jsize};
use new_post_parser_lib::{PostParserContext, PostParser};
use jni::errors::Error;

extern crate log;
extern crate android_logger;

use android_logger::Config;
use log::debug;
use crate::helpers::{format_post_parsing_object_signature, format_spannables_object_signature_pref, format_post_parsing_object_signature_pref};
use jni::errors::Error::NullPtr;

#[no_mangle]
pub unsafe extern fn Java_com_github_k1rakishou_chan_core_lib_KurobaNativeLib_init(
    env: JNIEnv,
    jclass: JClass
) {
    debug!("KurobaNativeLib_init");

    android_logger::init_once(
        Config::default()
          .with_min_level(log::Level::Debug)
          .format(|f, record| write!(f, "KurobaEx (native) |: {}", record.args()))
    );
}

#[no_mangle]
pub unsafe extern fn Java_com_github_k1rakishou_chan_core_lib_KurobaNativeLib_parseThreadPosts(
    env: JNIEnv,
    jclass: JClass,
    post_parser_context: JObject,
    threads_to_parse: JObject
) -> jobject {
    debug!("KurobaNativeLib_parseThreadPosts");

    let post_parser_context = post_parser_context_mapper::mapper::from_java_object(
        &env,
        post_parser_context
    ).expect("Failed to map post_parser_context");

    let thread_raw = thread_raw_mapper::mapper::from_java_object(
        &env,
        threads_to_parse
    ).expect("Failed to map thread_raw");

    let post_parser = PostParser::new(&post_parser_context);

    let post_thread_parsed_jclass = env.find_class(
        format_post_parsing_object_signature("PostThreadParsed").as_str()
    ).expect("Failed to find class PostThreadParsed");

    let post_thread_parsed_jobject = env.new_object(
        post_thread_parsed_jclass,
        "()V",
        &[]
    ).expect("Failed to instantiate PostThreadParsed");

    let post_comment_parsed_jclass = env.find_class(
        format_post_parsing_object_signature("PostCommentParsed").as_str()
    ).expect("Failed to find class PostCommentParsed");

    let post_comment_parsed_list = env.new_object_array(
        thread_raw.posts.len() as jsize,
        post_comment_parsed_jclass,
        JObject::null()
    ).expect("Failed to allocate array for spannables");

    for (index, post_raw) in thread_raw.posts.iter().enumerate() {
        let post_comment_parsed_maybe = post_parser.parse_post(&post_raw)
          .post_comment_parsed;

        let post_comment_parsed = if let Option::None = post_comment_parsed_maybe {
            env.set_object_array_element(
                post_comment_parsed_list,
                index as jsize,
                JObject::null()
            ).expect("Failed to add post_comment_parsed_jobject to post_comment_parsed_list");

            continue;
        } else {
            post_comment_parsed_maybe.unwrap()
        };

        let post_comment_parsed_jobject = post_comment_parsed_mapper::mapper::to_java_object(
            &env,
            &post_comment_parsed
        ).expect("post_comment_parsed_mapper::mapper::to_java_object error");

        env.set_object_array_element(
            post_comment_parsed_list,
            index as jsize,
            post_comment_parsed_jobject
        ).expect("Failed to add post_comment_parsed_jobject to post_comment_parsed_list");
    }

    env.set_field(
        post_thread_parsed_jobject,
        "postCommentsParsedList",
        format_post_parsing_object_signature_pref("[", "PostCommentParsed"),
        JValue::Object(JObject::from(post_comment_parsed_list))
    ).expect("Failed to set field postCommentsParsedList of post_thread_parsed_jobject");

    return post_thread_parsed_jobject.into_inner();
}