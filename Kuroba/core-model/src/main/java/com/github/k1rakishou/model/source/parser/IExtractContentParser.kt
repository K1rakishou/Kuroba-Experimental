package com.github.k1rakishou.model.source.parser

import com.github.k1rakishou.model.data.media.GenericVideoId
import com.github.k1rakishou.model.data.media.MediaServiceLinkExtraInfo
import com.github.k1rakishou.model.data.video_service.MediaServiceType
import okhttp3.ResponseBody

interface IExtractContentParser {
  fun parse(
    url: String,
    mediaServiceType: MediaServiceType,
    videoId: GenericVideoId,
    responseBody: ResponseBody
  ): MediaServiceLinkExtraInfo
}