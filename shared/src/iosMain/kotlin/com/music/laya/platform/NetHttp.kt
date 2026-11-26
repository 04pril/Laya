package com.music.laya.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

actual object NetHttp {
    private val client by lazy {
        HttpClient(Darwin) {
            expectSuccess = false
            followRedirects = true
            install(HttpTimeout)
        }
    }

    actual fun execute(request: HttpRequest): HttpResponse = try {
        runBlocking {
            val r = client.request(request.url) {
                method = HttpMethod.parse(request.method)
                request.headers.forEach { (k, v) -> header(k, v) }
                request.contentType?.let { contentType(ContentType.parse(it)) }
                request.body?.let { setBody(it) }
                    ?: if (request.method in setOf("POST", "PUT", "PATCH")) setBody(ByteArray(0)) else Unit
                timeout {
                    request.callTimeoutMs?.let { requestTimeoutMillis = it }
                    request.connectTimeoutMs?.let { connectTimeoutMillis = it }
                }
            }
            HttpResponse(r.status.value, r.headers.entries().associate { it.key to it.value }, r.bodyAsBytes(), r.call.request.url.toString())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw HttpException(e.message ?: "http failure", e)
    }

    /**
     * A GET whose body is read only until [readBytes] have arrived; the rest is never downloaded (leaving
     * `execute` drops the connection). Answers the status, the Content-Type and whether [readBytes] arrived.
     * Fails, as [execute] does, when nothing settles within [timeoutMs] (a body that stalls included).
     */
    fun probe(url: String, headers: Map<String, String>, readBytes: Long, timeoutMs: Long): Triple<Int, String?, Boolean> = try {
        runBlocking {
            withTimeoutOrNull(timeoutMs) {
                client.prepareGet(url) {
                    headers.forEach { (k, v) -> header(k, v) }
                }.execute { r ->
                    val channel = r.bodyAsChannel()
                    val buffer = ByteArray(16 * 1024)
                    var got = 0L
                    while (got < readBytes) {
                        val n = channel.readAvailable(buffer, 0, buffer.size)
                        if (n < 0) break
                        got += n
                    }
                    Triple(r.status.value, r.headers["Content-Type"], got >= readBytes)
                }
            } ?: throw HttpException("probe timed out after ${timeoutMs}ms", null)
        }
    } catch (e: HttpException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw HttpException(e.message ?: "http failure", e)
    }
}
