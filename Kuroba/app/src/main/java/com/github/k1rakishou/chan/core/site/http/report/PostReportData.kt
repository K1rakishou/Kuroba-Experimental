package com.github.k1rakishou.chan.core.site.http.report

import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.model.data.descriptor.PostDescriptor

sealed class PostReportData {
  object Empty : PostReportData()

  data class Chan4(
    val postDescriptor: PostDescriptor,
    val captchaInfo: CaptchaInfo,
    val catId: Int
  ) : PostReportData() {

    sealed class CaptchaInfo {
      object UsePasscode : CaptchaInfo()
      data class Solution(val captchaSolution: CaptchaSolution.ChallengeWithSolution) : CaptchaInfo()
    }

  }

  data class Dvach(
    val postDescriptor: PostDescriptor,
    val message: String
  ) : PostReportData()
}