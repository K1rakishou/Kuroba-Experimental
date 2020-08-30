package com.github.adamantcheese.chan.core.site.http.login

import com.github.adamantcheese.chan.core.site.SiteActions

abstract class AbstractLoginRequest(
  val type: SiteActions.LoginType
)