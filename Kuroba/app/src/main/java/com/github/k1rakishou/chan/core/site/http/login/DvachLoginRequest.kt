package com.github.k1rakishou.chan.core.site.http.login

import com.github.k1rakishou.chan.core.site.SiteActions

data class DvachLoginRequest(
  val passcode: String
) : AbstractLoginRequest(SiteActions.LoginType.Passcode)