package com.bycho.safereturnhome.data

import android.graphics.BitmapFactory
import android.util.Log
import com.bycho.safereturnhome.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Route analysis reads its own raw WMS image, independent of the display overlay and its alpha. */
internal object SafeMapRouteRiskRepository {
    private const val TAG = "SafeMapRouteRisk"
    private const val IMAGE_SIZE = 1024
    private const val CACHE_NANOS = 10L * 60 * 1_000_000_000
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()
    private data class CachedRaster(val raster: SafeMapRiskRaster, val savedAt: Long)
    private val rasters = ArrayDeque<CachedRaster>()
    private var legend: SafeMapLegend? = null
    private var legendSavedAt = 0L

    suspend fun load(routes: List<List<RouteCoordinate>>, marginMeters: Double = 60.0): SafeMapRiskRaster? {
        // If a wide candidate corridor would lose too much resolution, retain ordinary route analysis.
        val bounds = SafeMapBounds.forRoutes(routes, marginMeters)
            ?: SafeMapBounds.forRoutes(routes) ?: return null
        if (BuildConfig.SAFEMAP_API_KEY.isBlank()) return null
        return mutex.withLock {
            val now = System.nanoTime()
            rasters.removeAll { now - it.savedAt >= CACHE_NANOS }
            rasters.firstOrNull { it.raster.bounds.contains(bounds) }?.let { return@withLock it.raster }
            try {
                if (legend == null || now - legendSavedAt > 24L * 60 * 60 * 1_000_000_000) {
                    val bytes = download("lgdInfo", mapOf("intId" to "IF_0087"), 256_000)
                    legend = SafeMapLegend.fromResponse(bytes.toString(Charsets.UTF_8))
                        ?: return@withLock null
                    legendSavedAt = System.nanoTime()
                }
                val bytes = download("IF_0087_WMS", mapOf(
                    "service" to "WMS", "version" to "1.1.1", "request" to "GetMap",
                    "layers" to SafeMapLegend.LAYER, "styles" to SafeMapLegend.STYLE,
                    "srs" to "EPSG:4326", "bbox" to "${bounds.west},${bounds.south},${bounds.east},${bounds.north}",
                    "format" to "image/png", "width" to "$IMAGE_SIZE", "height" to "$IMAGE_SIZE",
                    "transparent" to "TRUE"
                ), 6_000_000)
                val raster = withContext(Dispatchers.Default) { decode(bounds, bytes, legend!!) }
                    ?: return@withLock null
                if (rasters.size >= 4) rasters.removeFirst()
                rasters.addLast(CachedRaster(raster, System.nanoTime()))
                Log.d(TAG, "Route raster loaded and cached.")
                raster
            } catch (error: Exception) {
                coroutineContext.ensureActive()
                // Exception messages/URLs can contain the service key. Never log them.
                Log.w(TAG, "Route analysis unavailable (${error.javaClass.simpleName}); using facility scoring.")
                null
            }
        }
    }

    private suspend fun decode(bounds: SafeMapBounds, bytes: ByteArray, palette: SafeMapLegend): SafeMapRiskRaster? {
        if (bytes.size < 8 || !bytes.take(8).toByteArray().contentEquals(
                byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth != IMAGE_SIZE || options.outHeight != IMAGE_SIZE) return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        try {
            val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
            bitmap.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)
            val grades = ByteArray(pixels.size)
            val colors = HashMap<Int, Byte>()
            for (index in pixels.indices) {
                if (index % IMAGE_SIZE == 0) coroutineContext.ensureActive()
                val pixel = pixels[index]
                if ((pixel ushr 24) < 64) continue
                grades[index] = colors.getOrPut(pixel) { palette.gradeOf(pixel).toByte() }
            }
            return SafeMapRiskRaster(bounds, IMAGE_SIZE, IMAGE_SIZE, grades)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun download(endpoint: String, params: Map<String, String>, maxBytes: Int): ByteArray {
        val url = "https://www.safemap.go.kr/openapi2/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("serviceKey", BuildConfig.SAFEMAP_API_KEY)
        params.forEach { (key, value) -> url.addQueryParameter(key, value) }
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(url.build()).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val bytes = response.use {
                            if (!it.isSuccessful) throw IOException("SafeMap response unavailable")
                            val body = it.body ?: throw IOException("Empty response")
                            if (body.contentLength() > maxBytes) throw IOException("Response too large")
                            body.byteStream().use { stream ->
                                val output = ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val size = stream.read(buffer)
                                    if (size < 0) break
                                    if (output.size() + size > maxBytes) throw IOException("Response too large")
                                    output.write(buffer, 0, size)
                                }
                                output.toByteArray()
                            }
                        }
                        if (continuation.isActive) continuation.resume(bytes)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
    }
}
