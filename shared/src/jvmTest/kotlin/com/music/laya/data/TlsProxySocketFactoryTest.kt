package com.music.laya.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.net.InetSocketAddress
import java.net.Proxy
import javax.net.ssl.SSLSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class TlsProxySocketFactoryTest {
    @Test fun httpOriginTravelsInsideVerifiedTlsToProxy() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = serverTls.sslContext().serverSocketFactory.createServerSocket(0)
        val worker = Executors.newSingleThreadExecutor()
        try {
            val received = worker.submit<String> {
                (server.accept() as SSLSocket).use { socket ->
                    socket.soTimeout = 3000
                    val input = socket.inputStream.bufferedReader()
                    val first = input.readLine()
                    while (input.readLine()?.isNotEmpty() == true) Unit
                    socket.outputStream.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                    first
                }
            }
            val client = OkHttpClient.Builder()
                .socketFactory(TlsProxySocketFactory("localhost", clientTls.sslSocketFactory()))
                .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", server.localPort)))
                .callTimeout(4, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url("http://unresolvable.invalid/download").build()).execute().use { assertEquals("ok", it.body.string()) }
            assertEquals("GET http://unresolvable.invalid/download HTTP/1.1", received.get(4,TimeUnit.SECONDS))
            client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()
        } finally { server.close();worker.shutdownNow() }
    }

    @Test fun wrongProxyHostnameIsRejectedBeforeHttpCredentialsCanBeSent() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = serverTls.sslContext().serverSocketFactory.createServerSocket(0)
        val worker = Executors.newSingleThreadExecutor()
        try {
            worker.submit { runCatching { (server.accept() as SSLSocket).use { it.soTimeout=3000;it.startHandshake() } } }
            val socket=TlsProxySocketFactory("wrong.invalid",clientTls.sslSocketFactory()).createSocket()
            assertFailsWith<javax.net.ssl.SSLHandshakeException> { socket.connect(InetSocketAddress("127.0.0.1",server.localPort),3000) }
            socket.close()
        } finally { server.close();worker.shutdownNow() }
    }
}
