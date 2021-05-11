pub mod mapper {
  use new_post_parser_lib::{Spannable, SpannableData, PostLink, ParsedPost};
  use jni::sys::{jobject, jsize, _jobject};
  use jni::{JNIEnv, errors};
  use crate::helpers::{format_post_parsing_object_signature, format_spannables_object_signature_pref, format_spannables_object_signature, format_post_parsing_object_signature_pref};
  use jni::objects::{JObject, JValue};

  pub fn map_to_post_parsed(env: &JNIEnv, parsed_post: &ParsedPost) -> errors::Result<jobject> {
    let post_comment_parsed_jclass = env.find_class(format_post_parsing_object_signature("ParsedSpannableText").as_str())
      .expect("Failed to find class ParsedSpannableText");
    let post_comment_parsed_jobject = env.new_object(post_comment_parsed_jclass, "()V", &[])
      .expect("Failed to instantiate ParsedSpannableText");

    env.set_field(
      post_comment_parsed_jobject,
      "commentTextRaw",
      "Ljava/lang/String;",
      JValue::Object(JObject::from(env.new_string(&parsed_post.post_comment_parsed.original_text)?.into_inner()))
    ).expect("Failed to set field commentTextRaw of post_comment_parsed_jobject");

    env.set_field(
      post_comment_parsed_jobject,
      "commentTextParsed",
      "Ljava/lang/String;",
      JValue::Object(JObject::from(env.new_string(&*parsed_post.post_comment_parsed.parsed_text)?.into_inner()))
    ).expect("Failed to set field commentTextParsed of post_comment_parsed_jobject");

    env.set_field(
      post_comment_parsed_jobject,
      "spannableList",
      format_spannables_object_signature_pref("[", "PostCommentSpannable").as_str(),
      JValue::Object(JObject::from(spannables_to_java_object(env, &*parsed_post.post_comment_parsed.spannables)?))
    ).expect("Failed to set field commentTextParsed of post_comment_parsed_jobject");

    let post_parsed_jclass = env.find_class(format_post_parsing_object_signature("PostParsed").as_str())
      .expect("Failed to find class PostParsed");
    let post_parsed_jobject = env.new_object(post_parsed_jclass, "()V", &[])
      .expect("Failed to instantiate PostParsed");

    env.set_field(post_parsed_jobject, "postId", "J", JValue::Long(parsed_post.post_id as i64))
      .expect("Failed to set field postId of post_parsed_jobject");
    env.set_field(post_parsed_jobject, "postSubId", "J", JValue::Long(parsed_post.post_sub_id as i64))
      .expect("Failed to set field postSubId of post_parsed_jobject");

    env.set_field(
      post_parsed_jobject,
      "postCommentParsed",
      format_post_parsing_object_signature_pref("", "ParsedSpannableText"),
      JValue::Object(JObject::from(post_comment_parsed_jobject))
    ).expect("Failed to set field postCommentParsed of post_parsed_jobject");

    return Result::Ok(post_parsed_jobject.into_inner());
  }

  fn spannables_to_java_object(env: &JNIEnv, spannables: &Vec<Spannable>) -> errors::Result<jobject> {
    let post_spannable_array_jclass = env.find_class(
      format_spannables_object_signature("PostCommentSpannable").as_str()
    ).expect("Failed to find class PostCommentSpannable");

    let post_spannable_array_jobject = env.new_object_array(
      spannables.len() as jsize,
      post_spannable_array_jclass,
      JObject::null()
    ).expect("Failed to allocate array for PostCommentSpannable");

    for (index, spannable) in spannables.iter().enumerate() {
      match &spannable.spannable_data {
        SpannableData::Link(post_link) => {
          match post_link {
            PostLink::Quote { post_no } => {
              let post_no_param = JValue::Long(*post_no as i64);
              let params = [post_no_param];

              add_post_comment_parsed_to_array(env, &spannable, post_spannable_array_jobject, index, "Quote", "(J)V", &params)?;
            }
            PostLink::Dead { post_no } => {
              let post_no_param = JValue::Long(*post_no as i64);
              let params = [post_no_param];

              add_post_comment_parsed_to_array(env, &spannable, post_spannable_array_jobject, index, "DeadQuote", "(J)V", &params)?;
            }
            PostLink::UrlLink { link } => {
              let link_param = JValue::Object(JObject::from(env.new_string(link)?.into_inner()));
              let params = [link_param];

              add_post_comment_parsed_to_array(env, &spannable, post_spannable_array_jobject, index, "UrlLink", "(Ljava/lang/String;)V", &params)?;
            }
            PostLink::BoardLink { board_code } => {
              let board_code_param = JValue::Object(JObject::from(env.new_string(board_code)?.into_inner()));
              let params = [board_code_param];

              add_post_comment_parsed_to_array(env, &spannable, post_spannable_array_jobject, index, "BoardLink", "(Ljava/lang/String;)V", &params)?;
            }
            PostLink::SearchLink { board_code, search_query } => {
              let board_code_param = JValue::Object(JObject::from(env.new_string(board_code)?.into_inner()));
              let search_query_param = JValue::Object(JObject::from(env.new_string(search_query)?.into_inner()));
              let params = [board_code_param, search_query_param];

              add_post_comment_parsed_to_array(env, &spannable, post_spannable_array_jobject, index, "SearchLink", "(Ljava/lang/String;Ljava/lang/String;)V", &params)?;
            }
            PostLink::ThreadLink { board_code, thread_no, post_no } => {
              let board_code_param = JValue::Object(JObject::from(env.new_string(board_code)?.into_inner()));
              let thread_no_param = JValue::Long(*thread_no as i64);
              let post_no_param = JValue::Long(*post_no as i64);
              let params = [board_code_param, thread_no_param, post_no_param];

              add_post_comment_parsed_to_array(env, &spannable, post_spannable_array_jobject, index, "ThreadLink", "(Ljava/lang/String;JJ)V", &params)?;
            }
          }
        }
        SpannableData::Spoiler => {
          add_post_comment_parsed_to_array(env, &spannable, post_spannable_array_jobject, index, "Spoiler", "()V", &[])?;
        }
        SpannableData::GreenText => {
          add_post_comment_parsed_to_array(env, &spannable, post_spannable_array_jobject, index, "GreenText", "()V", &[])?;
        }
        SpannableData::BoldText => {
          add_post_comment_parsed_to_array(env, &spannable, post_spannable_array_jobject, index, "BoldText", "()V", &[])?;
        }
        SpannableData::FontSize { size } => {
          let font_size_param = JValue::Object(JObject::from(env.new_string(size)?.into_inner()));
          let params = [font_size_param];

          add_post_comment_parsed_to_array(env, &spannable, post_spannable_array_jobject, index, "FontSize", "(Ljava/lang/String;)V", &params)?;
        }
        SpannableData::FontWeight { weight } => {
          let font_weight_param = JValue::Object(JObject::from(env.new_string(weight)?.into_inner()));
          let params = [font_weight_param];

          add_post_comment_parsed_to_array(env, &spannable, post_spannable_array_jobject, index, "FontWeight", "(Ljava/lang/String;)V", &params)?;
        }
        SpannableData::Monospace => {
          add_post_comment_parsed_to_array(env, &spannable, post_spannable_array_jobject, index, "Monospace", "()V", &[])?;
        }
        SpannableData::TextForegroundColorRaw { color_hex } => {
          let color_hex_param = JValue::Object(JObject::from(env.new_string(color_hex)?.into_inner()));
          let params = [color_hex_param];

          add_post_comment_parsed_to_array(env, &spannable, post_spannable_array_jobject, index, "TextForegroundColorRaw", "(Ljava/lang/String;)V", &params)?;
        }
        SpannableData::TextBackgroundColorRaw { color_hex } => {
          let color_hex_param = JValue::Object(JObject::from(env.new_string(color_hex)?.into_inner()));
          let params = [color_hex_param];

          add_post_comment_parsed_to_array(env, &spannable, post_spannable_array_jobject, index, "TextBackgroundColorRaw", "(Ljava/lang/String;)V", &params)?;
        }
        SpannableData::TextForegroundColorId { color_id } => {
          let color_id_param = JValue::Int(color_id.clone() as i32);
          let params = [color_id_param];

          add_post_comment_parsed_to_array(env, &spannable, post_spannable_array_jobject, index, "TextForegroundColorId", "I", &params)?;
        }
        SpannableData::TextBackgroundColorId { color_id } => {
          let color_id_param = JValue::Int(color_id.clone() as i32);
          let params = [color_id_param];

          add_post_comment_parsed_to_array(env, &spannable, post_spannable_array_jobject, index, "TextBackgroundColorId", "I", &params)?;
        }
      }
    }

    return Result::Ok(post_spannable_array_jobject);
  }

  fn add_post_comment_parsed_to_array(
    env: &JNIEnv,
    spannable: &Spannable,
    post_spannable_array_jobject: *mut _jobject,
    index: usize,
    spannable_class_signature: &str,
    ctor_sign: &str,
    ctor_args: &[JValue]
  ) -> errors::Result<()> {
    let spannable_data_full_type_string = format!("IPostCommentSpannableData${}", spannable_class_signature);
    let spannable_data_full_type = spannable_data_full_type_string.as_str();
    let spannable_data_signature_string = format_spannables_object_signature(spannable_data_full_type);

    let spannable_jclass = env.find_class(spannable_data_signature_string.as_str())
      .expect(format!("Failed to find class {}", spannable_data_full_type).as_str());

    let spannable_object = env.new_object(spannable_jclass, ctor_sign, ctor_args)
      .expect(format!("Failed to instantiate {}", spannable_data_full_type).as_str());

    let post_comment_spannable_jclass = env.find_class(
      format_spannables_object_signature("PostCommentSpannable").as_str()
    ).expect("Failed to find class PostCommentSpannable");

    let post_comment_spannable_object = env.new_object(post_comment_spannable_jclass, "()V", &[])
      .expect("Failed to instantiate PostCommentSpannable");

    env.set_field(post_comment_spannable_object, "start", "I", JValue::Int(spannable.start as i32))?;
    env.set_field(post_comment_spannable_object, "length", "I", JValue::Int(spannable.len as i32))?;

    env.set_field(
      post_comment_spannable_object,
      "spannableData",
      format_spannables_object_signature_pref("", "IPostCommentSpannableData"),
      JValue::Object(JObject::from(spannable_object.into_inner()))
    )?;

    env.set_object_array_element(
      post_spannable_array_jobject,
      index as jsize,
      post_comment_spannable_object
    )?;
    return Result::Ok(())
  }
}