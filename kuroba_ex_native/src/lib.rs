#![cfg(target_os = "android")]
#![allow(non_snake_case)]
#![deny(warnings)]

mod helpers;

mod mappers {
    pub mod post_parsed_mapper;
    pub mod post_parser_context_mapper;
    pub mod thread_raw_mapper;
}

mod usecases {
    pub mod parse_thread_posts_use_case;
}

use jni::JNIEnv;
use jni::objects::{JObject, JClass};
use jni::sys::{jobject};

extern crate log;
extern crate android_logger;

use android_logger::Config;
use log::debug;
use crate::usecases::parse_thread_posts_use_case::usecases::parse_thread_posts_use_case;

#[no_mangle]
pub unsafe extern fn Java_com_github_k1rakishou_chan_core_lib_KurobaNativeLib_init(
    _: JNIEnv,
    _: JClass
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
    _: JClass,
    post_parser_context: JObject,
    threads_to_parse: JObject
) -> jobject {
    debug!("KurobaNativeLib_parseThreadPosts");

    let result = parse_thread_posts_use_case(&env, post_parser_context, threads_to_parse);
    return match result {
        Ok(res) => res,
        Err(error) => {
            let exception_class = env.find_class("Lcom/github/k1rakishou/chan/core/lib/data/KurobaNativeLibException;")
              .expect("Failed to find KurobaNativeLibException class");
            let error_msg = format!("Unhandled error: {:?}", error);

            env.throw_new(exception_class, error_msg.as_str()).unwrap();
            return JObject::null().into_inner();
        },
    }
}