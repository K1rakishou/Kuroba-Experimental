package com.github.adamantcheese.chan.core.site.http.login

import com.github.adamantcheese.chan.core.site.SiteActions

data class DvachLoginRequest(
  val passcode: String
) : AbstractLoginRequest(SiteActions.LoginType.Passcode)