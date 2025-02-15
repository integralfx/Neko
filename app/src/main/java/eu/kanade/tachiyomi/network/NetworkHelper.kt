package eu.kanade.tachiyomi.network

import android.content.Context
import android.os.SystemClock
import eu.kanade.tachiyomi.BuildConfig
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File

class NetworkHelper(context: Context) {

    private val cacheDir = File(context.cacheDir, "network_cache")

    private val cacheSize = 5L * 1024 * 1024 // 5 MiB

    val cookieManager = AndroidCookieJar()

    private val requestsPerSecond = 4
    private val lastRequests = ArrayList<Long>(requestsPerSecond)
    private val rateLimitInterceptor = Interceptor {
        synchronized(this) {
            val now = SystemClock.elapsedRealtime()
            val waitTime = if (lastRequests.size < requestsPerSecond) {
                0
            } else {
                val oldestReq = lastRequests[0]
                val newestReq = lastRequests[requestsPerSecond - 1]

                if (newestReq - oldestReq > 1000) {
                    0
                } else {
                    oldestReq + 1000 - now // Remaining time for the next second
                }
            }

            if (lastRequests.size == requestsPerSecond) {
                lastRequests.removeAt(0)
            }
            if (waitTime > 0) {
                lastRequests.add(now + waitTime)
                Thread.sleep(waitTime) // Sleep inside synchronized to pause queued requests
            } else {
                lastRequests.add(now)
            }
        }

        it.proceed(it.request())
    }


    val client =
        if(BuildConfig.DEBUG) {
            OkHttpClient.Builder()
                    .cookieJar(cookieManager)
                    .cache(Cache(cacheDir, cacheSize))
                    .addNetworkInterceptor(rateLimitInterceptor)
                    .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                    .build()
        }else {
            OkHttpClient.Builder()
                    .cookieJar(cookieManager)
                    .cache(Cache(cacheDir, cacheSize))
                    .addNetworkInterceptor(rateLimitInterceptor)

                    .build()
        }

        val cloudflareClient = client.newBuilder()
                .addInterceptor(CloudflareInterceptor(context))
                .build()
    }

