package com.azimulkabir.actua.data.network

import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class OidcCallbackPageText(
    val languageTag: String,
    val completionTitle: String,
    val completionMessage: String,
    val returnToApp: String,
    val callbackErrorTitle: String,
    val callbackErrorMessage: String,
) {
    companion object {
        fun english() = OidcCallbackPageText(
            languageTag = "en",
            completionTitle = "Sign-in complete",
            completionMessage = "You are signed in to Actua.",
            returnToApp = "Return to Actua",
            callbackErrorTitle = "Sign-in could not be completed",
            callbackErrorMessage = "The callback did not contain an Actual session token. Return to Actua and try again.",
        )
    }
}

/**
 * Small one-shot loopback HTTP listener used only while the user completes Actual's OpenID flow.
 *
 * Actual accepts localhost as a return URL and redirects to /openid-cb?token=... after the provider
 * callback. Binding only to the loopback interface keeps the callback unreachable from the LAN.
 */
class OidcCallbackServer(
    private val pageText: OidcCallbackPageText = OidcCallbackPageText.english(),
) : Closeable {
    private val serverSocket = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
    }

    val returnUrl: String
        get() = "http://localhost:${serverSocket.localPort}"

    fun awaitToken(timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS): String {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!serverSocket.isClosed) {
            val remaining = (deadline - System.currentTimeMillis()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (remaining <= 0) throw SocketTimeoutException("OpenID sign-in timed out.")
            serverSocket.soTimeout = remaining

            val socket = serverSocket.accept()
            socket.use { client ->
                val reader = client.getInputStream().bufferedReader()
                val requestLine = reader.readLine().orEmpty()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }

                val target = requestLine.split(' ').getOrNull(1).orEmpty()
                val token = tokenFromTarget(target)
                if (token != null) {
                    writeAppRedirect(client)
                    return token
                }

                writeResponse(
                    client,
                    400,
                    pageText.callbackErrorTitle,
                    pageText.callbackErrorMessage,
                )
            }
        }
        throw IllegalStateException("OpenID callback listener was closed.")
    }

    override fun close() {
        runCatching { serverSocket.close() }
    }

    private fun tokenFromTarget(target: String): String? {
        if (!target.substringBefore('?').trimEnd('/').endsWith("/openid-cb")) return null
        val rawQuery = target.substringAfter('?', "")
        return rawQuery.split('&')
            .mapNotNull { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.size == 2) {
                    URLDecoder.decode(pieces[0], StandardCharsets.UTF_8.name()) to
                        URLDecoder.decode(pieces[1], StandardCharsets.UTF_8.name())
                } else null
            }
            .firstOrNull { it.first == "token" }
            ?.second
            ?.takeIf(String::isNotBlank)
    }

    private fun writeResponse(
        socket: java.net.Socket,
        status: Int,
        title: String,
        message: String,
    ) {
        val reason = if (status == 200) "OK" else "Bad Request"
        val escapedLanguageTag = pageText.languageTag.escapeHtml()
        val escapedTitle = title.escapeHtml()
        val escapedMessage = message.escapeHtml()
        val body = """
            <!doctype html>
            <html lang="$escapedLanguageTag">
              <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>$escapedTitle</title>
              </head>
              <body style="font-family:sans-serif;max-width:36rem;margin:4rem auto;padding:0 1.25rem;line-height:1.5">
                <h2>$escapedTitle</h2>
                <p>$escapedMessage</p>
              </body>
            </html>
        """.trimIndent()
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        socket.getOutputStream().bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.write("HTTP/1.1 $status $reason\r\n")
            writer.write("Content-Type: text/html; charset=utf-8\r\n")
            writer.write("Content-Length: ${bytes.size}\r\n")
            writer.write("Connection: close\r\n")
            writer.write("Cache-Control: no-store\r\n")
            writer.write("\r\n")
            writer.write(body)
            writer.flush()
        }
    }

    private fun writeAppRedirect(socket: java.net.Socket) {
        val escapedLanguageTag = pageText.languageTag.escapeHtml()
        val escapedTitle = pageText.completionTitle.escapeHtml()
        val escapedMessage = pageText.completionMessage.escapeHtml()
        val escapedReturnToApp = pageText.returnToApp.escapeHtml()
        val body = """
            <!doctype html>
            <html lang="$escapedLanguageTag">
              <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>$escapedTitle</title>
              </head>
              <body style="font-family:sans-serif;max-width:36rem;margin:4rem auto;padding:0 1.25rem;line-height:1.5">
                <h2>$escapedTitle</h2>
                <p>$escapedMessage</p>
                <p><a href="$APP_RETURN_URL">$escapedReturnToApp</a></p>
              </body>
            </html>
        """.trimIndent()
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        socket.getOutputStream().use { output ->
            output.write("HTTP/1.1 302 Found\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.write("Location: $APP_RETURN_URL\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.write("Content-Type: text/html; charset=utf-8\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.write("Content-Length: ${bytes.size}\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.write("Connection: close\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.write("Cache-Control: no-store\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.write(bytes)
            output.flush()
        }
    }

    companion object {
        private const val APP_RETURN_URL = "actua://oidc-complete"
        private const val DEFAULT_TIMEOUT_MILLIS = 5 * 60 * 1000
    }
}

private fun String.escapeHtml(): String = buildString(length) {
    this@escapeHtml.forEach { character ->
        append(when (character) {
            '&' -> "&amp;"
            '<' -> "&lt;"
            '>' -> "&gt;"
            '"' -> "&quot;"
            '\'' -> "&#39;"
            else -> character
        })
    }
}
