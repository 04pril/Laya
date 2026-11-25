package com.music.laya

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.music.laya.data.TlsProxySocketFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket

@RunWith(AndroidJUnit4::class)
class TlsProxyDeviceTest {
    @Test fun originTlsCanBeLayeredInsideTlsToTheProxyOnAndroid() {
        val certificate=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls=HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls=HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server=serverTls.sslContext().serverSocketFactory.createServerSocket(0)
        val worker=Executors.newSingleThreadExecutor()
        try {
            val received=worker.submit<String> {
                (server.accept() as SSLSocket).use { proxy ->
                    proxy.soTimeout=5000
                    val headers=proxy.inputStream.bufferedReader()
                    val connect=headers.readLine()
                    while(headers.readLine()?.isNotEmpty()==true) Unit
                    proxy.outputStream.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                    (serverTls.sslSocketFactory().createSocket(proxy,"localhost",server.localPort,false) as SSLSocket).use { origin ->
                        origin.useClientMode=false;origin.soTimeout=5000;origin.startHandshake()
                        val input=origin.inputStream.bufferedReader();val get=input.readLine()
                        while(input.readLine()?.isNotEmpty()==true) Unit
                        origin.outputStream.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                        "$connect|$get"
                    }
                }
            }
            val client=OkHttpClient.Builder()
                .socketFactory(TlsProxySocketFactory("localhost",clientTls.sslSocketFactory()))
                .sslSocketFactory(clientTls.sslSocketFactory(),clientTls.trustManager)
                .proxy(Proxy(Proxy.Type.HTTP,InetSocketAddress("127.0.0.1",server.localPort)))
                .callTimeout(8,TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url("https://localhost:9443/download").build()).execute().use { assertEquals("ok",it.body.string()) }
            assertEquals("CONNECT localhost:9443 HTTP/1.1|GET /download HTTP/1.1",received.get(8,TimeUnit.SECONDS))
            client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()
        } finally { server.close();worker.shutdownNow() }
    }
}
