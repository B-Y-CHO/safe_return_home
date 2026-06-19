package com.bycho.safereturnhome.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.bycho.safereturnhome.BuildConfig
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

data class StreetlightCoordinate(
    val latitude: Double,
    val longitude: Double,
    val lightCount: Int,
    val address: String,
    val fixtureType: String
)

class StreetlightRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadCoordinates(
        onCompleted: (List<StreetlightCoordinate>) -> Unit,
        onFailed: (String) -> Unit
    ) {
        val cachedCoordinates = loadCachedCoordinates()
        if (cachedCoordinates.isNotEmpty()) {
            onCompleted(cachedCoordinates)
            return
        }
        Thread {
            runCatching(::fetchCoordinates)
                .onSuccess { coordinates ->
                    saveCachedCoordinates(coordinates)
                    mainHandler.post { onCompleted(coordinates) }
                }
                .onFailure { error ->
                    mainHandler.post {
                        onFailed(error.message ?: "가로등 데이터를 가져오지 못했습니다")
                    }
                }
        }.start()
    }

    private fun fetchCoordinates(): List<StreetlightCoordinate> {
        check(BuildConfig.STREETLIGHT_API_KEY.isNotBlank()) { "가로등 API 인증키가 없습니다" }
        check(BuildConfig.STREETLIGHT_API_ENDPOINT.isNotBlank()) { "가로등 API 주소가 없습니다" }

        val firstPage = fetchPage(page = 1)
        val totalCount = firstPage.getInt("totalCount")
        val perPage = firstPage.getInt("perPage")
        val pageCount = ((totalCount + perPage - 1) / perPage).coerceAtLeast(1)
        return buildList {
            addAll(firstPage.getJSONArray("data").toCoordinates())
            for (page in 2..pageCount) {
                addAll(fetchPage(page).getJSONArray("data").toCoordinates())
            }
        }
    }

    private fun fetchPage(page: Int): JSONObject {
        val query = listOf(
            "page" to page.toString(),
            "perPage" to PAGE_SIZE.toString(),
            "serviceKey" to BuildConfig.STREETLIGHT_API_KEY
        ).joinToString("&") { (name, value) ->
            "$name=${URLEncoder.encode(value, StandardCharsets.UTF_8.name())}"
        }
        val connection = URI("${BuildConfig.STREETLIGHT_API_ENDPOINT}?$query")
            .toURL()
            .openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            check(connection.responseCode in 200..299) {
                "가로등 API 요청에 실패했습니다: HTTP ${connection.responseCode}"
            }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray.toCoordinates(): List<StreetlightCoordinate> {
        return buildList {
            for (index in 0 until length()) {
                val item = getJSONObject(index)
                val longitude = item.optString("X좌표").toDoubleOrNull() ?: continue
                val latitude = item.optString("Y좌표").toDoubleOrNull() ?: continue
                if (latitude !in GUMI_LATITUDE_RANGE || longitude !in GUMI_LONGITUDE_RANGE) continue
                add(
                    StreetlightCoordinate(
                        latitude = latitude,
                        longitude = longitude,
                        lightCount = item.optInt("총등수", 1).coerceAtLeast(1),
                        address = item.optString("지번 주소").ifBlank {
                            item.optString("분전함번호")
                        },
                        fixtureType = item.optString("등기구종류")
                    )
                )
            }
        }
    }

    private fun loadCachedCoordinates(): List<StreetlightCoordinate> {
        val cachedAtMillis = preferences.getLong(CACHE_TIMESTAMP_KEY, 0L)
        if (System.currentTimeMillis() - cachedAtMillis >= CACHE_DURATION_MILLIS) return emptyList()
        val encodedCoordinates = preferences.getString(CACHE_COORDINATES_KEY, null)
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(encodedCoordinates)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        StreetlightCoordinate(
                            latitude = item.getDouble("latitude"),
                            longitude = item.getDouble("longitude"),
                            lightCount = item.getInt("lightCount"),
                            address = item.getString("address"),
                            fixtureType = item.getString("fixtureType")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveCachedCoordinates(coordinates: List<StreetlightCoordinate>) {
        val encodedCoordinates = JSONArray().apply {
            coordinates.forEach { coordinate ->
                put(
                    JSONObject()
                        .put("latitude", coordinate.latitude)
                        .put("longitude", coordinate.longitude)
                        .put("lightCount", coordinate.lightCount)
                        .put("address", coordinate.address)
                        .put("fixtureType", coordinate.fixtureType)
                )
            }
        }
        preferences.edit()
            .putLong(CACHE_TIMESTAMP_KEY, System.currentTimeMillis())
            .putString(CACHE_COORDINATES_KEY, encodedCoordinates.toString())
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "streetlight_coordinates_v1"
        const val CACHE_TIMESTAMP_KEY = "cached_at"
        const val CACHE_COORDINATES_KEY = "coordinates"
        const val CACHE_DURATION_MILLIS = 23 * 60 * 60 * 1000L
        const val NETWORK_TIMEOUT_MILLIS = 10_000
        const val PAGE_SIZE = 1_000
        val GUMI_LATITUDE_RANGE = 35.7..37.0
        val GUMI_LONGITUDE_RANGE = 127.5..129.0
    }
}
