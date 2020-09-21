package com.github.k1rakishou.chan.core.site.http.login

import com.github.k1rakishou.chan.core.site.SiteActions

abstract class AbstractLoginRequest(
  val type: SiteActions.LoginType
)