package io.github.degipe.youtubewhitelist.core.network.interceptor

import com.google.common.truth.Truth.assertThat
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class RateLimitRetryInterceptorTest {

    private val request = Request.Builder().url("https://www.googleapis.com/youtube/v3/search").build()

    private fun response(code: Int, retryAfter: String? = null): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .apply { retryAfter?.let { header("Retry-After", it) } }
            .build()

    private fun chain(vararg codes: Int, calls: MutableList<Int> = mutableListOf()): Interceptor.Chain {
        var index = 0
        return object : Interceptor.Chain by NoChain(request) {
            override fun request(): Request = request
            override fun proceed(request: Request): Response {
                val code = codes[index.coerceAtMost(codes.lastIndex)]
                index++
                calls.add(code)
                return response(code)
            }
        }
    }

    @Test
    fun `a throttled request is retried and the later answer is used`() {
        val calls = mutableListOf<Int>()
        val waits = mutableListOf<Long>()
        val interceptor = RateLimitRetryInterceptor(sleep = { waits.add(it) })

        val result = interceptor.intercept(chain(429, 200, calls = calls))

        assertThat(result.code).isEqualTo(200)
        assertThat(calls).containsExactly(429, 200).inOrder()
        assertThat(waits).hasSize(1)
    }

    @Test
    fun `retrying stops after the configured attempts`() {
        val calls = mutableListOf<Int>()
        val interceptor = RateLimitRetryInterceptor(sleep = {})

        val result = interceptor.intercept(chain(429, calls = calls))

        assertThat(result.code).isEqualTo(429)
        assertThat(calls).hasSize(3) // erster Versuch plus zwei Wiederholungen
    }

    @Test
    fun `a successful answer is passed through untouched`() {
        val calls = mutableListOf<Int>()
        val interceptor = RateLimitRetryInterceptor(sleep = { error("must not wait") })

        val result = interceptor.intercept(chain(200, calls = calls))

        assertThat(result.code).isEqualTo(200)
        assertThat(calls).containsExactly(200)
    }

    @Test
    fun `client errors other than throttling are not retried`() {
        val calls = mutableListOf<Int>()
        val interceptor = RateLimitRetryInterceptor(sleep = { error("must not wait") })

        val result = interceptor.intercept(chain(403, calls = calls))

        assertThat(result.code).isEqualTo(403)
        assertThat(calls).containsExactly(403)
    }

    /** Minimal chain stub; only request() and proceed() are used. */
    private open class NoChain(private val request: Request) : Interceptor.Chain {
        override fun request(): Request = request
        override fun proceed(request: Request): Response = error("not used")
        override fun connection() = null
        override fun call() = error("not used")
        override fun connectTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun readTimeoutMillis() = 0
        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun writeTimeoutMillis() = 0
        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    }
}
