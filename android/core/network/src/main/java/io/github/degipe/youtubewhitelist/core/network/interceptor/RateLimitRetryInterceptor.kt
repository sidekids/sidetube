package io.github.degipe.youtubewhitelist.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Retries requests that Google throttled.
 *
 * The YouTube API buckets API-key traffic per client address. On a mobile
 * network several devices share one address, so a request can be answered with
 * 429 seconds after an identical one succeeded. The bucket refills quickly —
 * a short wait turns a hard failure into a normal answer.
 */
class RateLimitRetryInterceptor(
    private val maxRetries: Int = MAX_RETRIES,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())
        var attempt = 0

        while (response.code in RETRYABLE_CODES && attempt < maxRetries) {
            val waitMillis = response.retryAfterMillis() ?: BACKOFF_MILLIS[attempt]
            response.close()
            sleep(waitMillis)
            attempt++
            response = chain.proceed(chain.request())
        }

        return response
    }

    private fun Response.retryAfterMillis(): Long? =
        header("Retry-After")?.toLongOrNull()?.times(1000)?.coerceAtMost(MAX_WAIT_MILLIS)

    private companion object {
        const val MAX_RETRIES = 2
        const val MAX_WAIT_MILLIS = 5_000L
        val BACKOFF_MILLIS = longArrayOf(800L, 2_400L)
        val RETRYABLE_CODES = setOf(429, 500, 502, 503, 504)
    }
}
