package com.github.adamantcheese.chan.core.site.loader

import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.utils.errorMessageOrClassName
import com.google.gson.JsonParseException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLException


class ChanLoaderException(
        private val exception: Throwable
) : Exception(exception) {

    val isNotFound: Boolean
        get() = exception is ServerException && isServerErrorNotFound(exception)

    val errorMessage: Int
        get() {
            return when {
                exception is SocketTimeoutException
                        || exception is SocketException
                        || exception is UnknownHostException
                        || (exception is ServerException && exception.isAuthError()) -> {
                    R.string.thread_load_failed_network
                }
                exception is ServerException -> {
                    if (isServerErrorNotFound(exception)) {
                        R.string.thread_load_failed_not_found
                    } else {
                        R.string.thread_load_failed_server
                    }
                }
                exception is SSLException -> R.string.thread_load_failed_ssl
                exception is JsonParseException -> R.string.thread_load_failed_json_parsing
                else -> R.string.thread_load_failed_parsing
            }
        }

    fun toShortString() = exception.errorMessageOrClassName()

    private fun isServerErrorNotFound(exception: ServerException): Boolean {
        return exception.statusCode == 404
    }

    fun isCoroutineCancellationError(): Boolean {
        return exception is CancellationException
    }

}

class ServerException(val statusCode: Int) : Exception("Bad status code: ${statusCode}") {
    fun isAuthError(): Boolean {
        return statusCode in 400..499
    }
}