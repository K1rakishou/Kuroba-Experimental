package com.github.k1rakishou.common.dns

import okhttp3.Dns
import java.net.InetAddress

class DnsOverHttpsSelector(
  private val selector: Dns
) : Dns {
  override fun lookup(hostname: String): List<InetAddress> {
    return selector.lookup(hostname)
  }

}