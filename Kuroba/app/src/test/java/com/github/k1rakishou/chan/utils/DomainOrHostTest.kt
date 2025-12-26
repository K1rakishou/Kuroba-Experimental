package com.github.k1rakishou.chan.utils

import com.github.k1rakishou.common.domain
import com.github.k1rakishou.common.domainOrHost
import junit.framework.TestCase.assertEquals
import okhttp3.HttpUrl
import org.junit.Test

class DomainOrHostTest {

  @Test
  fun `validate that domain and host are retrieved correctly`() {
    val url = HttpUrl.Builder()
      .scheme("https")
      .host("sys.4chan.org")
      .build()

    assertEquals("sys.4chan.org", url.host)
    assertEquals("4chan.org", url.domain())
    assertEquals("4chan.org", url.domainOrHost())
  }

}