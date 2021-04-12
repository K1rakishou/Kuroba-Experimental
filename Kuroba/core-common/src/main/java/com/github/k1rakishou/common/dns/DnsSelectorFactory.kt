package com.github.k1rakishou.common.dns

import okhttp3.Dns
import okhttp3.OkHttpClient

interface DnsSelectorFactory<T : Dns> {
  fun createDnsSelector(okHttpClient: OkHttpClient): T
}

interface NormalDnsSelectorFactory : DnsSelectorFactory<NormalDnsSelector>
interface DnsOverHttpsSelectorFactory : DnsSelectorFactory<DnsOverHttpsSelector>