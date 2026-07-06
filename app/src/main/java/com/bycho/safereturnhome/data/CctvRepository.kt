package com.bycho.safereturnhome.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.bycho.safereturnhome.BuildConfig
import com.skt.tmap.TMapData
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

data class CctvCoordinate(
    val latitude: Double,
    val longitude: Double,
    val cameraCount: Int,
    val managementNumber: String,
    val address: String,
    val routeEligible: Boolean = true,
    val routePriority: Int = 0
)

internal fun CctvCoordinate.isRouteEligibleCandidate(): Boolean {
    return routeEligible
}

class CctvRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadCoordinates(
        addressHints: List<String>,
        hasEnoughCoordinates: (List<CctvCoordinate>) -> Boolean,
        onProgress: (String) -> Unit,
        onCompleted: (List<CctvCoordinate>) -> Unit,
        onFailed: (String) -> Unit,
        stopWhenEnoughCoordinates: Boolean = true,
        maxGeocodingAttempts: Int = MAX_GEOCODING_ATTEMPTS
    ) {
        val cachedCoordinates = loadCachedCoordinates().toMutableList()
        if (stopWhenEnoughCoordinates && hasEnoughCoordinates(cachedCoordinates)) {
            onCompleted(cachedCoordinates)
            return
        }
        onProgress("구미시 CCTV 목록을 불러오는 중입니다.")
        Thread {
            runCatching(::fetchInstallations)
                .onSuccess { installations ->
                    mainHandler.post {
                        resolveCoordinates(
                            installations = installations,
                            addressHints = addressHints,
                            cachedCoordinates = cachedCoordinates,
                            hasEnoughCoordinates = hasEnoughCoordinates,
                            stopWhenEnoughCoordinates = stopWhenEnoughCoordinates,
                            maxGeocodingAttempts = maxGeocodingAttempts,
                            onProgress = onProgress,
                            onCompleted = onCompleted,
                            onFailed = onFailed
                        )
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        onFailed(error.message ?: "CCTV 데이터를 가져오지 못했습니다")
                    }
                }
        }.start()
    }

    private fun fetchInstallations(): List<CctvInstallation> {
        check(BuildConfig.CCTV_API_KEY.isNotBlank()) { "CCTV API 인증키가 없습니다" }
        val endpoints = listOf(
            BuildConfig.CCTV_API_ENDPOINT,
            BuildConfig.CCTV_CRIME_PREVENTION_API_ENDPOINT,
            BuildConfig.CCTV_CHILD_PARK_API_ENDPOINT
        ).filter(String::isNotBlank)
        check(endpoints.isNotEmpty()) { "CCTV API 주소가 없습니다" }
        val failures = mutableListOf<Throwable>()
        val installations = endpoints.flatMap { endpoint ->
            runCatching { fetchInstallations(endpoint) }
                .onFailure(failures::add)
                .getOrDefault(emptyList())
        }
        if (installations.isEmpty() && failures.isNotEmpty()) {
            throw IllegalStateException(
                failures.first().message ?: "CCTV 데이터를 가져오지 못했습니다"
            )
        }
        return installations
    }

    private fun fetchInstallations(endpoint: String): List<CctvInstallation> {
        val query = listOf(
            "serviceKey" to BuildConfig.CCTV_API_KEY,
            "pageNo" to "1",
            "numOfRows" to "1000"
        ).joinToString("&") { (name, value) ->
            "$name=${URLEncoder.encode(value, StandardCharsets.UTF_8.name())}"
        }
        val connection = URI("$endpoint?$query")
            .toURL()
            .openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            check(connection.responseCode in 200..299) {
                "CCTV API 요청에 실패했습니다: HTTP ${connection.responseCode}"
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(response)
            val header = root.getJSONObject("header")
            check(header.optString("resultCode") == "00") {
                "CCTV API 오류: ${header.optString("resultMsg", "알 수 없는 오류")}"
            }
            root.getJSONArray("body").toInstallations(
                routePriority = if (endpoint == BuildConfig.CCTV_CHILD_PARK_API_ENDPOINT) {
                    SUPPLEMENTAL_ROUTE_PRIORITY
                } else {
                    PRIMARY_ROUTE_PRIORITY
                }
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveCoordinates(
        installations: List<CctvInstallation>,
        addressHints: List<String>,
        cachedCoordinates: MutableList<CctvCoordinate>,
        hasEnoughCoordinates: (List<CctvCoordinate>) -> Boolean,
        stopWhenEnoughCoordinates: Boolean,
        maxGeocodingAttempts: Int,
        onProgress: (String) -> Unit,
        onCompleted: (List<CctvCoordinate>) -> Unit,
        onFailed: (String) -> Unit
    ) {
        val cachedAddresses = cachedCoordinates.mapTo(mutableSetOf(), CctvCoordinate::address)
        val pendingInstallations = installations
            .mergeByAddress()
            .asSequence()
            .filterNot { it.address in cachedAddresses }
            .sortedBy { installationPriority(it, addressHints) }
            .take(maxGeocodingAttempts)
            .toList()
        if (pendingInstallations.isEmpty()) {
            completeLoading(cachedCoordinates, hasEnoughCoordinates, onCompleted, onFailed)
            return
        }

        val tMapData = TMapData()
        var nextIndex = 0
        var completedCount = 0
        var inFlightCount = 0
        var isFinished = false

        fun complete() {
            if (isFinished) return
            isFinished = true
            saveCachedCoordinates(cachedCoordinates)
            completeLoading(cachedCoordinates, hasEnoughCoordinates, onCompleted, onFailed)
        }

        fun startMoreRequests() {
            if (isFinished) return
            if (stopWhenEnoughCoordinates && hasEnoughCoordinates(cachedCoordinates)) {
                complete()
                return
            }
            if (nextIndex >= pendingInstallations.size && inFlightCount == 0) {
                complete()
                return
            }
            while (
                !isFinished &&
                inFlightCount < MAX_CONCURRENT_GEOCODING_REQUESTS &&
                nextIndex < pendingInstallations.size
            ) {
                val installation = pendingInstallations[nextIndex++]
                inFlightCount += 1
                var isRequestCompleted = false
                lateinit var timeout: Runnable

                fun finishAttempt(coordinate: CctvCoordinate?) {
                    if (isRequestCompleted || isFinished) return
                    isRequestCompleted = true
                    mainHandler.removeCallbacks(timeout)
                    coordinate?.let(cachedCoordinates::add)
                    completedCount += 1
                    inFlightCount -= 1
                    onProgress(
                        "CCTV 위치를 준비하는 중입니다: " +
                            "$completedCount/${pendingInstallations.size}"
                    )
                    startMoreRequests()
                }

                timeout = Runnable { finishAttempt(null) }
                mainHandler.postDelayed(timeout, GEOCODING_TIMEOUT_MILLIS)
                runCatching {
                    tMapData.findAddressPOI(
                        installation.address,
                        1,
                        object : TMapData.OnFindAddressPOILinstener {
                            override fun onFindAddress(
                                poiItems: ArrayList<com.skt.tmap.poi.TMapPOIItem>?
                            ) {
                                mainHandler.post {
                                    val coordinate = poiItems?.firstOrNull()?.poiPoint?.let { point ->
                                        CctvCoordinate(
                                            latitude = point.latitude,
                                            longitude = point.longitude,
                                            cameraCount = installation.cameraCount,
                                            managementNumber = installation.managementNumber,
                                            address = installation.address,
                                            routeEligible = installation.routeEligible,
                                            routePriority = installation.routePriority
                                        )
                                    }
                                    finishAttempt(coordinate)
                                }
                            }
                        }
                    )
                }.onFailure {
                    finishAttempt(null)
                }
            }
        }

        onProgress("경로 주변 CCTV 위치를 준비하는 중입니다.")
        startMoreRequests()
    }

    private fun completeLoading(
        coordinates: List<CctvCoordinate>,
        hasEnoughCoordinates: (List<CctvCoordinate>) -> Boolean,
        onCompleted: (List<CctvCoordinate>) -> Unit,
        onFailed: (String) -> Unit
    ) {
        if (hasEnoughCoordinates(coordinates) || coordinates.isNotEmpty()) {
            onCompleted(coordinates)
        } else {
            onFailed("경로 주변에서 좌표가 확인된 CCTV를 찾지 못했습니다")
        }
    }

    private fun installationPriority(
        installation: CctvInstallation,
        addressHints: List<String>
    ): Int {
        if (
            installation.districtName.isNotBlank() &&
            addressHints.any { hint -> hint.contains(installation.districtName) }
        ) {
            return 0
        }
        return if (
            addressHints
                .flatMap { hint -> hint.split(' ') }
                .filter { token -> token.length >= 2 }
                .any(installation.address::contains)
        ) {
            1
        } else {
            2
        }
    }

    private fun List<CctvInstallation>.mergeByAddress(): List<CctvInstallation> {
        return groupBy(CctvInstallation::address).map { (_, installations) ->
            installations.first().copy(
                cameraCount = installations.sumOf(CctvInstallation::cameraCount),
                managementNumber = installations
                    .map(CctvInstallation::managementNumber)
                    .filter(String::isNotBlank)
                    .joinToString(","),
                routeEligible = installations.all(CctvInstallation::routeEligible),
                routePriority = installations.minOf(CctvInstallation::routePriority)
            )
        }
    }

    private fun loadCachedCoordinates(): List<CctvCoordinate> {
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
                        CctvCoordinate(
                            latitude = item.getDouble("latitude"),
                            longitude = item.getDouble("longitude"),
                            cameraCount = item.getInt("cameraCount"),
                            managementNumber = item.getString("managementNumber"),
                            address = item.getString("address"),
                            routeEligible = item.optBoolean("routeEligible", true),
                            routePriority = item.optInt("routePriority", PRIMARY_ROUTE_PRIORITY)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveCachedCoordinates(coordinates: List<CctvCoordinate>) {
        val encodedCoordinates = JSONArray().apply {
            coordinates.distinctBy(CctvCoordinate::address).forEach { coordinate ->
                put(
                    JSONObject()
                        .put("latitude", coordinate.latitude)
                        .put("longitude", coordinate.longitude)
                        .put("cameraCount", coordinate.cameraCount)
                        .put("managementNumber", coordinate.managementNumber)
                        .put("address", coordinate.address)
                        .put("routeEligible", coordinate.routeEligible)
                        .put("routePriority", coordinate.routePriority)
                )
            }
        }
        preferences.edit()
            .putLong(CACHE_TIMESTAMP_KEY, System.currentTimeMillis())
            .putString(CACHE_COORDINATES_KEY, encodedCoordinates.toString())
            .apply()
    }

    private fun JSONArray.toInstallations(
        routePriority: Int
    ): List<CctvInstallation> {
        return buildList {
            for (index in 0 until length()) {
                val item = getJSONObject(index)
                val address = item.firstNonBlankString(
                    "nw_adres",
                    "old_adres",
                    "rn_adres",
                    "road_nm_adres",
                    "road_nm_addr",
                    "lnm_adres",
                    "jibun_adres",
                    "adres",
                    "addr",
                    "address",
                    "소재지도로명주소",
                    "소재지지번주소"
                )
                    .lineSequence()
                    .firstOrNull()
                    .orEmpty()
                    .trim()
                if (address.isBlank()) continue
                val managementNumber = item.firstNonBlankString(
                    "manage_no",
                    "mng_no",
                    "park_nm",
                    "fclty_nm",
                    "시설명"
                )
                add(
                    CctvInstallation(
                        address = address.withGumiPrefix(),
                        districtName = item.firstNonBlankString(
                            "emd_nm",
                            "dong_nm",
                            "읍면동명"
                        ),
                        cameraCount = item.firstPositiveInt(
                            "camera_qy",
                            "cctv_qy",
                            "cctv_co",
                            "cctv_cnt",
                            "camera_cnt",
                            "설치대수"
                        ),
                        managementNumber = managementNumber,
                        routeEligible = true,
                        routePriority = routePriority
                    )
                )
            }
        }
    }

    private fun JSONObject.firstNonBlankString(vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            optString(key).trim().takeIf(String::isNotBlank)
        }.orEmpty()
    }

    private fun JSONObject.firstPositiveInt(vararg keys: String): Int {
        return keys.firstNotNullOfOrNull { key ->
            optInt(key, 0).takeIf { it > 0 }
        } ?: 1
    }

    private fun String.withGumiPrefix(): String {
        return when {
            contains("구미") -> this
            startsWith("경상북도") -> this
            startsWith("경북") -> this
            else -> "경상북도 구미시 $this"
        }
    }

    private data class CctvInstallation(
        val address: String,
        val districtName: String,
        val cameraCount: Int,
        val managementNumber: String,
        val routeEligible: Boolean,
        val routePriority: Int
    )

    private companion object {
        const val PREFERENCES_NAME = "cctv_coordinates_v6"
        const val CACHE_TIMESTAMP_KEY = "cached_at"
        const val CACHE_COORDINATES_KEY = "coordinates"
        const val CACHE_DURATION_MILLIS = 23 * 60 * 60 * 1000L
        const val NETWORK_TIMEOUT_MILLIS = 10_000
        const val GEOCODING_TIMEOUT_MILLIS = 2_000L
        const val MAX_CONCURRENT_GEOCODING_REQUESTS = 4
        const val MAX_GEOCODING_ATTEMPTS = 80
        const val PRIMARY_ROUTE_PRIORITY = 0
        const val SUPPLEMENTAL_ROUTE_PRIORITY = 1
    }
}
