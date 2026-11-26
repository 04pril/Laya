package com.music.laya.data

import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** TLS to the proxy, followed by OkHttp's ordinary CONNECT and destination TLS.
 * Keeps proxy certificate/hostname verification separate from the origin's. */
class TlsProxySocketFactory(
    private val proxyHost: String,
    private val tls: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory,
) : SocketFactory() {
    override fun createSocket(): Socket = ProxySocket()
    override fun createSocket(host: String, port: Int) = createSocket().apply { connect(java.net.InetSocketAddress(host, port)) }
    override fun createSocket(host: InetAddress, port: Int) = createSocket().apply { connect(java.net.InetSocketAddress(host, port)) }
    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int) =
        createSocket().apply { bind(java.net.InetSocketAddress(local, localPort)); connect(java.net.InetSocketAddress(host, port)) }
    override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int) =
        createSocket().apply { bind(java.net.InetSocketAddress(local, localPort)); connect(java.net.InetSocketAddress(host, port)) }

    private inner class ProxySocket : Socket() {
        private var transport: Socket = Socket()
        override fun connect(endpoint: SocketAddress) = connect(endpoint, 0)
        override fun connect(endpoint: SocketAddress, timeout: Int) {
            try {
                transport.connect(endpoint, timeout)
                val encrypted = tls.createSocket(transport, proxyHost, transport.port, true) as SSLSocket
                encrypted.sslParameters = encrypted.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                transport = encrypted
                encrypted.startHandshake()
            } catch (e: Exception) { transport.close(); throw e }
        }
        override fun bind(address: SocketAddress?) = transport.bind(address)
        override fun getInputStream() = transport.getInputStream()
        override fun getOutputStream() = transport.getOutputStream()
        override fun close() = transport.close()
        override fun shutdownInput() = transport.shutdownInput()
        override fun shutdownOutput() = transport.shutdownOutput()
        override fun getInetAddress() = transport.inetAddress
        override fun getLocalAddress() = transport.localAddress
        override fun getPort() = transport.port
        override fun getLocalPort() = transport.localPort
        override fun getRemoteSocketAddress() = transport.remoteSocketAddress
        override fun getLocalSocketAddress() = transport.localSocketAddress
        override fun isConnected() = transport.isConnected
        override fun isBound() = transport.isBound
        override fun isClosed() = transport.isClosed
        override fun isInputShutdown() = transport.isInputShutdown
        override fun isOutputShutdown() = transport.isOutputShutdown
        override fun setSoTimeout(value: Int) { transport.soTimeout = value }
        override fun getSoTimeout() = transport.soTimeout
        override fun setTcpNoDelay(value: Boolean) { transport.tcpNoDelay = value }
        override fun getTcpNoDelay() = transport.tcpNoDelay
        override fun setKeepAlive(value: Boolean) { transport.keepAlive = value }
        override fun getKeepAlive() = transport.keepAlive
        override fun setReuseAddress(value: Boolean) { transport.reuseAddress = value }
        override fun getReuseAddress() = transport.reuseAddress
        override fun setSoLinger(on: Boolean, linger: Int) = transport.setSoLinger(on, linger)
        override fun getSoLinger() = transport.soLinger
        override fun setSendBufferSize(size: Int) { transport.sendBufferSize = size }
        override fun getSendBufferSize() = transport.sendBufferSize
        override fun setReceiveBufferSize(size: Int) { transport.receiveBufferSize = size }
        override fun getReceiveBufferSize() = transport.receiveBufferSize
    }
}
