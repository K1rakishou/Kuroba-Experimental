package com.github.adamantcheese.database.source.remote

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer

@Suppress("BlockingMethodInNonBlockingContext")
internal fun withServer(func: suspend (MockWebServer) -> Unit) {
    val server = MockWebServer()

    try {
        runBlocking { func(server) }
    } finally {
        server.shutdown()
    }
}
