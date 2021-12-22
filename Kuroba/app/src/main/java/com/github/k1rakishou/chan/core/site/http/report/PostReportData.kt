package com.github.k1rakishou.chan.core.site.http.report

import com.github.k1rakishou.chan.ui.captcha.CaptchaSolution
import com.github.k1rakishou.model.data.descriptor.PostDescriptor

sealed class PostReportData {
  object Empty : PostReportData()

  data class Chan4(
    val postDescriptor: PostDescriptor,
    val captchaSolution: CaptchaSolution.ChallengeWithSolution,
    val catId: Int
  ) : PostReportData()

  data class Dvach(
    val postDescriptor: PostDescriptor,
    val message: String
  ) : PostReportData()
}