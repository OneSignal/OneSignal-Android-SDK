package com.onesignal.tests.core.internal.http

import com.onesignal.core.internal.http.impl.IHttpConnectionFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

internal class MockHttpConnectionFactory(
    var mockResponse: MockResponse
) : IHttpConnectionFactory {

    val connections: MutableList<MockHttpURLConnection> = mutableListOf()
    var lastConnection: MockHttpURLConnection? = null

    override fun newHttpURLConnection(url: String): HttpURLConnection {
        lastConnection = MockHttpURLConnection(URL("https://onesignal.com/api/v1/$url"), mockResponse)
        connections.add(lastConnection!!)
        return lastConnection as HttpURLConnection
    }

    class MockResponse {
        var responseBody: String? = null
        var errorResponseBody: String? = null
        var mockRequestTime: Long? = null
        var status = 0
        var mockProps: MutableMap<String, String> = mutableMapOf()
    }

    class MockHttpURLConnection(
        url: URL?,
        private val mockResponse: MockResponse
    ) : HttpURLConnection(url) {
        override fun disconnect() {}
        override fun usingProxy(): Boolean {
            return false
        }

        @Throws(IOException::class)
        override fun connect() {
        }

        override fun getHeaderField(name: String): String {
            return mockResponse.mockProps[name]!!
        }

        @Throws(IOException::class)
        override fun getResponseCode(): Int {
            if (mockResponse.mockRequestTime != null) {
                try {
                    Thread.sleep(mockResponse.mockRequestTime!!)
                } catch (e: InterruptedException) {
                    throw IOException("Successfully interrupted stuck thread!")
                }
            }

            return mockResponse.status
        }

        override fun getOutputStream(): OutputStream {
            return NullOutputStream()
        }

        @Throws(IOException::class)
        override fun getInputStream(): InputStream {
            val bytes = mockResponse.responseBody!!.toByteArray()
            return ByteArrayInputStream(bytes)
        }

        override fun getErrorStream(): InputStream {
            if (mockResponse.errorResponseBody == null) {
                throw Exception("No error response body")
            }

            val bytes = mockResponse.errorResponseBody!!.toByteArray()
            return ByteArrayInputStream(bytes)
        }
    }

    private class NullOutputStream : OutputStream() {
        override fun write(p0: Int) {
        }
    }
}
