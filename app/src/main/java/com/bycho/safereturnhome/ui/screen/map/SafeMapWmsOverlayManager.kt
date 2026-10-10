package com.bycho.safereturnhome.ui.screen.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.bycho.safereturnhome.BuildConfig
import com.skt.tmap.TMapPoint
import com.skt.tmap.TMapView
import com.skt.tmap.overlay.TMapOverlay
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

object SafeMapWmsOverlayManager {
    private const val TAG = "SafeMapWmsOverlay"
    private const val OVERLAY_ID = "safeMapWmsOverlay"

    private var isEnabled = false
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun toggle(view: TMapView, enable: Boolean) {
        isEnabled = enable
        if (enable) {
            updateOverlay(view)
        } else {
            clear(view)
        }
    }

    fun clear(view: TMapView) {
        job?.cancel()
        runCatching { view.removeTMapOverlay(OVERLAY_ID) }
    }

    fun onMapBoundsChanged(view: TMapView) {
        if (!isEnabled) return
        updateOverlay(view)
    }

    private fun updateOverlay(view: TMapView) {
        job?.cancel()
        job = scope.launch {
            delay(400) // Debounce — wait for map to settle
            try {
                val leftTop = view.leftTopPoint ?: return@launch
                val rightBottom = view.rightBottomPoint ?: return@launch

                val minLon = leftTop.longitude
                val maxLat = leftTop.latitude
                val maxLon = rightBottom.longitude
                val minLat = rightBottom.latitude

                Log.d(TAG, "Requesting WMS tile: bbox=$minLon,$minLat,$maxLon,$maxLat")

                val bitmap = fetchWmsTile(minLon, minLat, maxLon, maxLat)

                if (bitmap != null && isActive) {
                    Log.d(TAG, "WMS tile received: ${bitmap.width}x${bitmap.height}")
                    // Remove old overlay before adding new one
                    runCatching { view.removeTMapOverlay(OVERLAY_ID) }
                    val overlay = TMapOverlay().apply {
                        id = OVERLAY_ID
                        this.leftTopPoint = TMapPoint(maxLat, minLon)
                        this.rightBottomPoint = TMapPoint(minLat, maxLon)
                        overlayImage = bitmap
                        alpha = 180
                        priority = 50f
                    }
                    view.addTMapOverlay(overlay)
                    Log.d(TAG, "WMS overlay added to map")
                } else if (bitmap == null) {
                    Log.w(TAG, "WMS tile returned null bitmap")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update WMS overlay", e)
            }
        }
    }

    private suspend fun fetchWmsTile(
        minLon: Double,
        minLat: Double,
        maxLon: Double,
        maxLat: Double
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val serviceKey = BuildConfig.SAFEMAP_API_KEY
            if (serviceKey.isBlank()) {
                Log.e(TAG, "SAFEMAP_API_KEY is blank!")
                return@withContext null
            }

            // WMS 표준 필수 파라미터 포함
            val bbox = "$minLon,$minLat,$maxLon,$maxLat"
            val urlString = "http://safemap.go.kr/openapi2/IF_0087_WMS" +
                "?serviceKey=$serviceKey" +
                "&service=WMS" +
                "&version=1.1.1" +
                "&request=GetMap" +
                "&layers=A2SM_CRMNLHSPOT_TOT" +
                "&styles=A2SM_CrmnlHspot_Tot_Tot" +
                "&srs=EPSG:4326" +
                "&bbox=$bbox" +
                "&format=image/png" +
                "&width=1024" +
                "&height=1024" +
                "&transparent=TRUE"

            Log.d(TAG, "WMS URL: $urlString")

            var currentUrl = urlString
            var redirectCount = 0
            val maxRedirects = 5

            while (redirectCount < maxRedirects) {
                val url = URL(currentUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.instanceFollowRedirects = false // 수동으로 리다이렉트 처리

                val responseCode = connection.responseCode
                Log.d(TAG, "WMS response code: $responseCode (attempt ${redirectCount + 1})")

                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location")
                    if (location.isNullOrBlank()) {
                        Log.e(TAG, "Redirect with no Location header")
                        return@withContext null
                    }
                    Log.d(TAG, "Redirecting to: $location")
                    currentUrl = if (location.startsWith("http")) location else {
                        // 상대 경로 리다이렉트 처리
                        val base = url.protocol + "://" + url.host
                        base + location
                    }
                    connection.disconnect()
                    redirectCount++
                    continue
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val contentType = connection.contentType
                    Log.d(TAG, "WMS content-type: $contentType")

                    val inputStream = connection.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    connection.disconnect()

                    if (bitmap == null) {
                        Log.e(TAG, "BitmapFactory returned null — response may be XML error")
                    }
                    return@withContext bitmap
                } else {
                    val errorStream = connection.errorStream
                    val errorBody = errorStream?.bufferedReader()?.use { it.readText() } ?: "no body"
                    Log.e(TAG, "API Error $responseCode: $errorBody")
                    connection.disconnect()
                    return@withContext null
                }
            }

            Log.e(TAG, "Too many redirects ($maxRedirects)")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch WMS tile", e)
            null
        }
    }
}
