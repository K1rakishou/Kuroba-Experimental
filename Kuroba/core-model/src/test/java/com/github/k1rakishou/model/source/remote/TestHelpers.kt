package com.github.k1rakishou.model.source.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer

@Suppress("BlockingMethodInNonBlockingContext")
internal fun withServer(func: suspend (MockWebServer) -> Unit) {
  val server = MockWebServer()

  try {
    runBlocking(Dispatchers.Default) { func(server) }
  } finally {
    server.shutdown()
  }
}
