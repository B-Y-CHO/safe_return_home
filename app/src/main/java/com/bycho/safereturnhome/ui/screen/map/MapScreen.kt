package com.bycho.safereturnhome.ui.screen.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import android.util.Log
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bycho.safereturnhome.BuildConfig
import com.bycho.safereturnhome.R
import com.bycho.safereturnhome.ui.state.DestinationSearchResult
import com.bycho.safereturnhome.ui.state.MapUiState
import com.bycho.safereturnhome.ui.viewmodel.MapViewModel
import com.skt.tmap.TMapData
import com.skt.tmap.TMapPoint
import com.skt.tmap.TMapView
import com.skt.tmap.overlay.TMapCircle
import com.skt.tmap.overlay.TMapMarkerItem
import kotlin.math.abs
import java.util.Locale

private const val TMAP_LOG_TAG = "TMapViewContainer"
private const val DEFAULT_LATITUDE = 37.5665
private const val DEFAULT_LONGITUDE = 126.9780
private const val CURRENT_LOCATION_ACCURACY_CIRCLE_ID = "current-location-accuracy"
private const val CURRENT_LOCATION_MARKER_ID = "current-location-marker"
private const val DESTINATION_MARKER_ID = "destination-marker"
private const val PEDESTRIAN_ROUTE_ID = "pedestrian-route"
private const val LOCATION_UPDATE_INTERVAL_MILLIS = 2_500L
private const val LOCATION_UPDATE_DISTANCE_METERS = 3f
private const val MIN_HEADING_CHANGE_DEGREES = 2f
private const val HEADING_SMOOTHING_FACTOR = 0.18f
private const val ROUTE_DEVIATION_THRESHOLD_METERS = 50f
private const val ROUTE_DEVIATION_CONFIRMATION_COUNT = 3
private const val DESTINATION_ARRIVAL_THRESHOLD_METERS = 20f

private class MapRenderState {
    var centeredLatitude: Double? = null
    var centeredLongitude: Double? = null
    var recenterRequestId: Int = -1
    var accuracyLatitude: Double? = null
    var accuracyLongitude: Double? = null
    var accuracyMeters: Float? = null
    var destination: DestinationSearchResult? = null
    var hasCurrentLocationMarker: Boolean = false
    var routeSearchRequestId: Int = 0
}

private data class RemainingRouteMetrics(
    val remainingDistanceMeters: Int,
    val distanceFromRouteMeters: Float
)

@Composable
fun MapRoute(
    onBackClick: () -> Unit,
    onConfirmRouteClick: () -> Unit,
    viewModel: MapViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    MapScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onConfirmRouteClick = onConfirmRouteClick,
        onMapReady = viewModel::onMapReady,
        onApiKeyFailed = viewModel::onApiKeyFailed,
        onLocationPermissionResult = viewModel::onLocationPermissionResult,
        onCurrentLocationLoaded = viewModel::onCurrentLocationLoaded,
        onCurrentLocationUnavailable = viewModel::onCurrentLocationUnavailable,
        onDestinationQueryChanged = viewModel::onDestinationQueryChanged,
        onDestinationSearchStarted = viewModel::onDestinationSearchStarted,
        onDestinationSearchCompleted = viewModel::onDestinationSearchCompleted,
        onDestinationSearchFailed = viewModel::onDestinationSearchFailed,
        onDestinationSelected = viewModel::onDestinationSelected,
        onRouteSearchStarted = viewModel::onRouteSearchStarted,
        onRouteSearchCompleted = viewModel::onRouteSearchCompleted,
        onRouteSearchFailed = viewModel::onRouteSearchFailed,
        onRemainingRouteDistanceChanged = viewModel::onRemainingRouteDistanceChanged,
        onRouteRecalculationStarted = viewModel::onRouteRecalculationStarted,
        onDestinationArrived = viewModel::onDestinationArrived
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    uiState: MapUiState,
    onBackClick: () -> Unit,
    onConfirmRouteClick: () -> Unit,
    onMapReady: () -> Unit,
    onApiKeyFailed: (String?) -> Unit,
    onLocationPermissionResult: (Boolean) -> Unit,
    onCurrentLocationLoaded: (Double, Double, Float, Float) -> Unit,
    onCurrentLocationUnavailable: (String) -> Unit,
    onDestinationQueryChanged: (String) -> Unit,
    onDestinationSearchStarted: () -> Unit,
    onDestinationSearchCompleted: (List<DestinationSearchResult>) -> Unit,
    onDestinationSearchFailed: (String) -> Unit,
    onDestinationSelected: (DestinationSearchResult) -> Unit,
    onRouteSearchStarted: () -> Unit,
    onRouteSearchCompleted: (Int) -> Unit,
    onRouteSearchFailed: (String) -> Unit,
    onRemainingRouteDistanceChanged: (Int) -> Unit,
    onRouteRecalculationStarted: () -> Unit,
    onDestinationArrived: () -> Unit
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    var recenterRequestId by remember { mutableIntStateOf(0) }
    var destinationFocusRequestId by remember { mutableIntStateOf(0) }
    var routeSearchRequestId by remember { mutableIntStateOf(0) }
    var isFollowingCurrentLocation by remember { mutableStateOf(true) }
    var isNavigationMode by remember { mutableStateOf(false) }
    var isNorthUpMode by remember { mutableStateOf(false) }
    var deviceHeadingDegrees by remember { mutableFloatStateOf(Float.NaN) }
    var tMapView by remember { mutableStateOf<TMapView?>(null) }
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    var activeRoutePoints by remember { mutableStateOf<List<TMapPoint>>(emptyList()) }
    var consecutiveRouteDeviationCount by remember { mutableIntStateOf(0) }
    var isRouteRecalculationInProgress by remember { mutableStateOf(false) }
    var hasArrivedAtDestination by remember { mutableStateOf(false) }
    val onTrackedLocationLoaded: (Double, Double, Float, Float) -> Unit =
        { latitude, longitude, accuracyMeters, bearingDegrees ->
            onCurrentLocationLoaded(latitude, longitude, accuracyMeters, bearingDegrees)
            val destination = uiState.selectedDestination
            if (activeRoutePoints.isNotEmpty() && destination != null && !hasArrivedAtDestination) {
                val metrics = calculateRemainingRouteMetrics(
                        currentLatitude = latitude,
                        currentLongitude = longitude,
                        routePoints = activeRoutePoints
                )
                onRemainingRouteDistanceChanged(metrics.remainingDistanceMeters)

                val distanceToDestinationMeters = calculateDistanceMeters(
                    latitude,
                    longitude,
                    destination.latitude,
                    destination.longitude
                )
                if (distanceToDestinationMeters <= DESTINATION_ARRIVAL_THRESHOLD_METERS) {
                    hasArrivedAtDestination = true
                    isRouteRecalculationInProgress = false
                    consecutiveRouteDeviationCount = 0
                    onDestinationArrived()
                    speak(textToSpeech, "목적지에 도착했습니다.")
                    vibrateArrival(context)
                } else if (!isRouteRecalculationInProgress) {
                    consecutiveRouteDeviationCount =
                        if (metrics.distanceFromRouteMeters >= ROUTE_DEVIATION_THRESHOLD_METERS) {
                            consecutiveRouteDeviationCount + 1
                        } else {
                            0
                        }
                    if (consecutiveRouteDeviationCount >= ROUTE_DEVIATION_CONFIRMATION_COUNT) {
                        consecutiveRouteDeviationCount = 0
                        isRouteRecalculationInProgress = true
                        onRouteRecalculationStarted()
                        speak(textToSpeech, "경로를 벗어났습니다. 경로를 다시 검색합니다.")
                        vibrateRouteRecalculation(context)
                        routeSearchRequestId += 1
                    }
                }
            }
        }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onLocationPermissionResult(isGranted)
        if (isGranted) {
            fetchCurrentLocation(
                context = context,
                onLocationLoaded = onTrackedLocationLoaded,
                onLocationUnavailable = onCurrentLocationUnavailable
            )
        }
    }

    LaunchedEffect(uiState.hasApiKey) {
        if (!uiState.hasApiKey) return@LaunchedEffect

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        onLocationPermissionResult(hasPermission)
        if (hasPermission) {
            fetchCurrentLocation(
                context = context,
                onLocationLoaded = onTrackedLocationLoaded,
                onLocationUnavailable = onCurrentLocationUnavailable
            )
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    DisposableEffect(uiState.hasApiKey, uiState.isLocationPermissionGranted) {
        if (!uiState.hasApiKey || !uiState.isLocationPermissionGranted) {
            onDispose {}
        } else {
            val stopLocationUpdates = startCurrentLocationUpdates(
                context = context,
                onLocationLoaded = onTrackedLocationLoaded,
                onLocationUnavailable = onCurrentLocationUnavailable
            )
            onDispose(stopLocationUpdates)
        }
    }

    DisposableEffect(context) {
        val stopHeadingUpdates = startDeviceHeadingUpdates(
            context = context,
            onHeadingChanged = { deviceHeadingDegrees = it }
        )
        onDispose(stopHeadingUpdates)
    }

    DisposableEffect(context) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
                textToSpeech = tts
            }
        }
        onDispose {
            textToSpeech = null
            tts?.stop()
            tts?.shutdown()
        }
    }

    DisposableEffect(rootView, isNavigationMode) {
        rootView.keepScreenOn = isNavigationMode
        onDispose {
            rootView.keepScreenOn = false
        }
    }

    LaunchedEffect(
        tMapView,
        isNavigationMode,
        isFollowingCurrentLocation,
        isNorthUpMode,
        deviceHeadingDegrees
    ) {
        val view = tMapView ?: return@LaunchedEffect
        val rotationDegrees = if (
            isNavigationMode &&
            isFollowingCurrentLocation &&
            !isNorthUpMode &&
            !deviceHeadingDegrees.isNaN()
        ) {
            -deviceHeadingDegrees.toDouble()
        } else {
            0.0
        }
        view.setRotationAngle(rotationDegrees)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNavigationMode) "경로 안내" else "경로 설정") },
                navigationIcon = {
                    if (!isNavigationMode) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "뒤로 가기"
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isNavigationMode) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.destinationQuery,
                    onValueChange = onDestinationQueryChanged,
                    label = { Text("목적지 검색") },
                    placeholder = { Text("장소명 또는 주소") },
                    singleLine = true
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.isMapReady &&
                        uiState.destinationQuery.isNotBlank() &&
                        !uiState.isDestinationSearchInProgress,
                    onClick = {
                        onDestinationSearchStarted()
                        searchDestinationPoi(
                            context = context,
                            query = uiState.destinationQuery,
                            onSearchCompleted = onDestinationSearchCompleted,
                            onSearchFailed = onDestinationSearchFailed
                        )
                    }
                ) {
                    Text(if (uiState.isDestinationSearchInProgress) "검색 중..." else "검색")
                }
                uiState.destinationSearchMessage?.let { message ->
                    Text(text = message)
                }
                uiState.destinationSearchResults.take(5).forEach { result ->
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            isFollowingCurrentLocation = false
                            hasArrivedAtDestination = false
                            isRouteRecalculationInProgress = false
                            consecutiveRouteDeviationCount = 0
                            activeRoutePoints = emptyList()
                            destinationFocusRequestId += 1
                            onDestinationSelected(result)
                        }
                    ) {
                        Text("${result.name}\n${result.address}")
                    }
                }
                Text(
                    text = "지도를 불러온 뒤 현재 위치를 확인하고, 다음 단계에서 목적지와 경로를 연결합니다.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isNavigationMode) 560.dp else 280.dp)
                    .padding(bottom = 4.dp)
            ) {
                TMapViewContainer(
                    modifier = Modifier.fillMaxSize(),
                    apiKey = if (uiState.hasApiKey) BuildConfig.TMAP_API_KEY else "",
                    hasApiKey = uiState.hasApiKey,
                    latitude = uiState.currentLatitude,
                    longitude = uiState.currentLongitude,
                    accuracyMeters = uiState.currentLocationAccuracyMeters,
                    bearingDegrees = if (
                        isNavigationMode &&
                        isFollowingCurrentLocation &&
                        !isNorthUpMode
                    ) {
                        0f
                    } else {
                        deviceHeadingDegrees.takeUnless(Float::isNaN)
                            ?: uiState.currentLocationBearingDegrees
                    },
                    recenterRequestId = recenterRequestId,
                    destination = uiState.selectedDestination,
                    destinationFocusRequestId = destinationFocusRequestId,
                    routeSearchRequestId = routeSearchRequestId,
                    isFollowingCurrentLocation = isFollowingCurrentLocation,
                    onMapInteraction = { isFollowingCurrentLocation = false },
                    onMapReady = onMapReady,
                    onApiKeyFailed = onApiKeyFailed,
                    onMapViewCreated = { tMapView = it },
                    onRouteSearchCompleted = { distanceMeters ->
                        val isFirstRouteSearch = !isNavigationMode
                        isRouteRecalculationInProgress = false
                        onRouteSearchCompleted(distanceMeters)
                        isNavigationMode = true
                        if (isFirstRouteSearch) {
                            speak(textToSpeech, "경로 안내를 시작합니다.")
                            vibrateNavigationStarted(context)
                        }
                    },
                    onRouteSearchFailed = { reason ->
                        isRouteRecalculationInProgress = false
                        onRouteSearchFailed(reason)
                    },
                    onRoutePointsChanged = { activeRoutePoints = it }
                )
                Button(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    enabled = uiState.currentLatitude != null && uiState.currentLongitude != null,
                    onClick = {
                        isFollowingCurrentLocation = true
                        recenterRequestId += 1
                        val latitude = uiState.currentLatitude
                        val longitude = uiState.currentLongitude
                        if (latitude != null && longitude != null) {
                            tMapView?.setZoomLevel(17)
                            tMapView?.setCenterPoint(latitude, longitude)
                        }
                    }
                ) {
                    Text("내 위치")
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { tMapView?.mapZoomIn() }) {
                        Text("+")
                    }
                    Button(onClick = { tMapView?.mapZoomOut() }) {
                        Text("-")
                    }
                }
                if (isNavigationMode) {
                    Button(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        onClick = { isNorthUpMode = !isNorthUpMode }
                    ) {
                        Text(if (isNorthUpMode) "진행 방향" else "북쪽 고정")
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = uiState.routeSummary)
                    if (isNavigationMode) {
                        LinearProgressIndicator(
                            progress = { uiState.routeProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(text = "경로 진행률: ${(uiState.routeProgress * 100).toInt()}%")
                    }
                    if (isNavigationMode) {
                        uiState.routeSearchMessage?.let { Text(text = it) }
                    } else {
                        Text(text = uiState.mapStatusLabel)
                        Text(text = uiState.currentLocationLabel)
                        Text(text = uiState.gpsSignalLabel)
                        Text(text = if (isFollowingCurrentLocation) "자동 추적: 켜짐" else "자동 추적: 꺼짐")
                        Text(text = uiState.destinationLabel)
                        uiState.routeSearchMessage?.let { Text(text = it) }
                    }
                }
            }
            if (isNavigationMode) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        isNavigationMode = false
                        isNorthUpMode = false
                    }
                ) {
                    Text("경로 안내 종료")
                }
            } else {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.currentLatitude != null &&
                        uiState.currentLongitude != null &&
                        uiState.selectedDestination != null &&
                        !uiState.isRouteSearchInProgress,
                    onClick = {
                        onRouteSearchStarted()
                        routeSearchRequestId += 1
                    }
                ) {
                    Text("이 경로로 시작")
                }
            }
        }
    }
}

@Composable
private fun TMapViewContainer(
    modifier: Modifier,
    apiKey: String,
    hasApiKey: Boolean,
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float?,
    bearingDegrees: Float,
    recenterRequestId: Int,
    destination: DestinationSearchResult?,
    destinationFocusRequestId: Int,
    routeSearchRequestId: Int,
    isFollowingCurrentLocation: Boolean,
    onMapInteraction: () -> Unit,
    onMapReady: () -> Unit,
    onApiKeyFailed: (String?) -> Unit,
    onMapViewCreated: (TMapView) -> Unit,
    onRouteSearchCompleted: (Int) -> Unit,
    onRouteSearchFailed: (String) -> Unit,
    onRoutePointsChanged: (List<TMapPoint>) -> Unit
) {
    var lastDestinationFocusRequestId by remember { mutableIntStateOf(0) }
    val renderState = remember { MapRenderState() }

    if (!hasApiKey) {
        Card(modifier = modifier) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "TMap API 키가 없어 지도를 시작할 수 없습니다.",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TMapView(context).apply {
                onMapViewCreated(this)
                Log.i(TMAP_LOG_TAG, "Creating TMap view.")
                setOnApiKeyListenerCallback(object : TMapView.OnApiKeyListenerCallback {
                    override fun onSKTMapApikeySucceed() {
                        Log.i(TMAP_LOG_TAG, "TMap API key authentication succeeded.")
                    }

                    override fun onSKTMapApikeyFailed(message: String?) {
                        Log.e(TMAP_LOG_TAG, "TMap API key authentication failed: ${message ?: "unknown error"}")
                        post { onApiKeyFailed(message) }
                    }
                })
                setOnMapReadyListener(object : TMapView.OnMapReadyListener {
                    override fun onMapReady() {
                        post {
                            Log.i(TMAP_LOG_TAG, "TMap view is ready.")
                            runCatching {
                                setZoomLevel(15)
                                if (latitude != null && longitude != null) {
                                    setCenterPoint(latitude, longitude)
                                } else {
                                    setCenterPoint(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
                                }
                            }.onFailure { error ->
                                Log.e(TMAP_LOG_TAG, "Failed to initialize map camera.", error)
                            }
                            onMapReady()
                        }
                    }
                })
                setOnMapGestureListener(object : TMapView.OnMapGestureListenerCallback() {
                    override fun onDown(event: android.view.MotionEvent): Boolean {
                        onMapInteraction()
                        return false
                    }
                })
                setSKTMapApiKey(apiKey)
            }
        },
        update = { view ->
            if (latitude != null && longitude != null) {
                Log.d(
                    TMAP_LOG_TAG,
                    "Updating current location: $latitude, $longitude (follow: $isFollowingCurrentLocation, request: $recenterRequestId)"
                )
                runCatching {
                    view.setLocationPoint(latitude, longitude)
                    val shouldRecenter = isFollowingCurrentLocation &&
                        (renderState.centeredLatitude != latitude ||
                            renderState.centeredLongitude != longitude ||
                            renderState.recenterRequestId != recenterRequestId)
                    if (shouldRecenter) {
                        view.setZoomLevel(17)
                        view.setCenterPoint(latitude, longitude)
                        renderState.centeredLatitude = latitude
                        renderState.centeredLongitude = longitude
                        renderState.recenterRequestId = recenterRequestId
                    }
                }.onFailure { error ->
                    Log.e(TMAP_LOG_TAG, "Failed to move map to current location.", error)
                }
                runCatching {
                    val currentLocationMarker = createCurrentLocationMarker(
                        latitude = latitude,
                        longitude = longitude,
                        bearingDegrees = bearingDegrees
                    )
                    if (renderState.hasCurrentLocationMarker) {
                        view.updateTMapMarkerItem(currentLocationMarker)
                    } else {
                        view.addTMapMarkerItem(currentLocationMarker)
                        renderState.hasCurrentLocationMarker = true
                    }
                }.onFailure { error ->
                    Log.w(TMAP_LOG_TAG, "Failed to show the current location marker.", error)
                }
                val shouldUpdateAccuracyCircle = accuracyMeters != null &&
                    (renderState.accuracyLatitude != latitude ||
                        renderState.accuracyLongitude != longitude ||
                        renderState.accuracyMeters != accuracyMeters)
                if (shouldUpdateAccuracyCircle) {
                    runCatching {
                        view.removeTMapCircle(CURRENT_LOCATION_ACCURACY_CIRCLE_ID)
                        view.addTMapCircle(
                            createCurrentLocationAccuracyCircle(
                                latitude = latitude,
                                longitude = longitude,
                                accuracyMeters = accuracyMeters
                            )
                        )
                        renderState.accuracyLatitude = latitude
                        renderState.accuracyLongitude = longitude
                        renderState.accuracyMeters = accuracyMeters
                    }.onFailure { error ->
                        Log.w(TMAP_LOG_TAG, "Failed to show GPS accuracy circle.", error)
                    }
                }
            }
            if (destination != null) {
                runCatching {
                    if (renderState.destination != destination) {
                        view.removeTMapMarkerItem(DESTINATION_MARKER_ID)
                        view.addTMapMarkerItem(createDestinationMarker(destination))
                        renderState.destination = destination
                    }
                    if (lastDestinationFocusRequestId != destinationFocusRequestId) {
                        view.setZoomLevel(16)
                        view.setCenterPoint(destination.latitude, destination.longitude)
                        lastDestinationFocusRequestId = destinationFocusRequestId
                    }
                }.onFailure { error ->
                    Log.w(TMAP_LOG_TAG, "Failed to show destination marker.", error)
                }
            }
            if (
                routeSearchRequestId > renderState.routeSearchRequestId &&
                latitude != null &&
                longitude != null &&
                destination != null
            ) {
                renderState.routeSearchRequestId = routeSearchRequestId
                findAndShowPedestrianRoute(
                    view = view,
                    startPoint = TMapPoint(latitude, longitude),
                    destinationPoint = TMapPoint(destination.latitude, destination.longitude),
                    onRouteSearchCompleted = onRouteSearchCompleted,
                    onRouteSearchFailed = onRouteSearchFailed,
                    onRoutePointsChanged = onRoutePointsChanged
                )
            }
        }
    )
}

private fun createCurrentLocationIcon(bearingDegrees: Float): Bitmap {
    val size = 96
    val center = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(33, 150, 243)
        style = Paint.Style.FILL
    }
    val arrowPath = Path().apply {
        moveTo(center, 10f)
        lineTo(78f, 78f)
        lineTo(center, 64f)
        lineTo(18f, 78f)
        close()
    }

    canvas.drawCircle(center, center, 33f, backgroundPaint)
    canvas.rotate(bearingDegrees, center, center)
    canvas.drawPath(arrowPath, arrowPaint)
    return bitmap
}

private fun createCurrentLocationMarker(
    latitude: Double,
    longitude: Double,
    bearingDegrees: Float
): TMapMarkerItem {
    return TMapMarkerItem().apply {
        setId(CURRENT_LOCATION_MARKER_ID)
        setTMapPoint(TMapPoint(latitude, longitude))
        setIcon(createCurrentLocationIcon(bearingDegrees))
        setPosition(0.5f, 0.5f)
        setVisible(true)
    }
}

private fun createDestinationMarker(destination: DestinationSearchResult): TMapMarkerItem {
    return TMapMarkerItem().apply {
        setId(DESTINATION_MARKER_ID)
        setTMapPoint(TMapPoint(destination.latitude, destination.longitude))
        setIcon(createDestinationIcon())
        setPosition(0.5f, 1f)
        setVisible(true)
    }
}

private fun createDestinationIcon(): Bitmap {
    val size = 72
    val center = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(244, 67, 54)
        style = Paint.Style.FILL
    }
    val path = Path().apply {
        moveTo(center, size.toFloat())
        lineTo(10f, 26f)
        arcTo(10f, 0f, 62f, 52f, 180f, 180f, false)
        close()
    }
    canvas.drawPath(path, paint)
    return bitmap
}

private fun createCurrentLocationAccuracyCircle(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float
): TMapCircle {
    return TMapCircle().apply {
        setId(CURRENT_LOCATION_ACCURACY_CIRCLE_ID)
        setCenterPoint(TMapPoint(latitude, longitude))
        setRadius(accuracyMeters.coerceAtLeast(5f).toDouble())
        setAreaColor(Color.rgb(33, 150, 243))
        setAreaAlpha(35)
        setLineColor(Color.rgb(33, 150, 243))
        setLineAlpha(100)
        setCircleWidth(2f)
    }
}

private fun findAndShowPedestrianRoute(
    view: TMapView,
    startPoint: TMapPoint,
    destinationPoint: TMapPoint,
    onRouteSearchCompleted: (Int) -> Unit,
    onRouteSearchFailed: (String) -> Unit,
    onRoutePointsChanged: (List<TMapPoint>) -> Unit
) {
    runCatching {
        TMapData().findPathDataWithType(
            TMapData.TMapPathType.PEDESTRIAN_PATH,
            startPoint,
            destinationPoint,
            object : TMapData.OnFindPathDataWithTypeListener {
                override fun onFindPathDataWithType(polyLine: com.skt.tmap.overlay.TMapPolyLine?) {
                    view.post {
                        if (polyLine == null || polyLine.linePointList.isEmpty()) {
                            onRouteSearchFailed("경로 검색 결과가 없습니다")
                            return@post
                        }
                        runCatching {
                            polyLine.setID(PEDESTRIAN_ROUTE_ID)
                            polyLine.setLineColor(Color.rgb(33, 150, 243))
                            polyLine.setLineWidth(8f)
                            polyLine.setLineAlpha(220)
                            view.removeTMapPath()
                            view.setTMapPath(polyLine)
                            view.fitBounds(view.getBoundsFromPoints(polyLine.linePointList))
                            onRoutePointsChanged(polyLine.linePointList.toList())
                            onRouteSearchCompleted(calculatePolylineDistanceMeters(polyLine))
                        }.onFailure { error ->
                            Log.e(TMAP_LOG_TAG, "Failed to show pedestrian route.", error)
                            onRouteSearchFailed(error.message ?: "unknown error")
                        }
                    }
                }
            }
        )
    }.onFailure { error ->
        Log.e(TMAP_LOG_TAG, "Failed to search pedestrian route.", error)
        view.post {
            onRouteSearchFailed(error.message ?: "unknown error")
        }
    }
}

private fun calculatePolylineDistanceMeters(
    polyLine: com.skt.tmap.overlay.TMapPolyLine
): Int {
    val sdkDistanceMeters = polyLine.distance.toInt()
    if (sdkDistanceMeters > 0) return sdkDistanceMeters

    return polyLine.linePointList
        .zipWithNext()
        .sumOf { (startPoint, endPoint) ->
            val segmentDistanceMeters = FloatArray(1)
            Location.distanceBetween(
                startPoint.latitude,
                startPoint.longitude,
                endPoint.latitude,
                endPoint.longitude,
                segmentDistanceMeters
            )
            segmentDistanceMeters[0].toDouble()
        }
        .toInt()
}

private fun calculateRemainingRouteMetrics(
    currentLatitude: Double,
    currentLongitude: Double,
    routePoints: List<TMapPoint>
): RemainingRouteMetrics {
    if (routePoints.isEmpty()) return RemainingRouteMetrics(0, 0f)

    val distanceToRoutePoint = FloatArray(1)
    val nearestPointIndex = routePoints.indices.minByOrNull { index ->
        val routePoint = routePoints[index]
        Location.distanceBetween(
            currentLatitude,
            currentLongitude,
            routePoint.latitude,
            routePoint.longitude,
            distanceToRoutePoint
        )
        distanceToRoutePoint[0]
    } ?: return RemainingRouteMetrics(0, 0f)

    val nearestPoint = routePoints[nearestPointIndex]
    Location.distanceBetween(
        currentLatitude,
        currentLongitude,
        nearestPoint.latitude,
        nearestPoint.longitude,
        distanceToRoutePoint
    )
    val distanceToNearestPoint = distanceToRoutePoint[0].toDouble()
    val remainingPolylineDistance = routePoints
        .drop(nearestPointIndex)
        .zipWithNext()
        .sumOf { (startPoint, endPoint) ->
            val segmentDistanceMeters = FloatArray(1)
            Location.distanceBetween(
                startPoint.latitude,
                startPoint.longitude,
                endPoint.latitude,
                endPoint.longitude,
                segmentDistanceMeters
            )
            segmentDistanceMeters[0].toDouble()
        }

    return RemainingRouteMetrics(
        remainingDistanceMeters = (distanceToNearestPoint + remainingPolylineDistance).toInt(),
        distanceFromRouteMeters = distanceToNearestPoint.toFloat()
    )
}

private fun calculateDistanceMeters(
    startLatitude: Double,
    startLongitude: Double,
    endLatitude: Double,
    endLongitude: Double
): Float {
    val distanceMeters = FloatArray(1)
    Location.distanceBetween(
        startLatitude,
        startLongitude,
        endLatitude,
        endLongitude,
        distanceMeters
    )
    return distanceMeters[0]
}

private fun speak(textToSpeech: TextToSpeech?, message: String) {
    textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, message)
}

private fun vibrateNavigationStarted(context: Context) {
    vibrate(context, longArrayOf(0, 120))
}

private fun vibrateRouteRecalculation(context: Context) {
    vibrate(context, longArrayOf(0, 100, 100, 100))
}

private fun vibrateArrival(context: Context) {
    vibrate(context, longArrayOf(0, 180, 120, 180, 120, 300))
}

@Suppress("DEPRECATION")
private fun vibrate(context: Context, pattern: LongArray) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return

    if (!vibrator.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    } else {
        vibrator.vibrate(pattern, -1)
    }
}

private fun searchDestinationPoi(
    context: Context,
    query: String,
    onSearchCompleted: (List<DestinationSearchResult>) -> Unit,
    onSearchFailed: (String) -> Unit
) {
    runCatching {
        TMapData().findAllPOI(
            query.trim(),
            10,
            object : TMapData.OnFindAllPOIListener {
                override fun onFindAllPOI(poiItems: ArrayList<com.skt.tmap.poi.TMapPOIItem>?) {
                    val results = poiItems.orEmpty().mapNotNull { poiItem ->
                        val point = poiItem.poiPoint ?: return@mapNotNull null
                        DestinationSearchResult(
                            name = poiItem.poiName.orEmpty(),
                            address = poiItem.poiAddress.orEmpty(),
                            latitude = point.latitude,
                            longitude = point.longitude
                        )
                    }
                    ContextCompat.getMainExecutor(context).execute {
                        onSearchCompleted(results)
                    }
                }
            }
        )
    }.onFailure { error ->
        Log.e(TMAP_LOG_TAG, "Failed to search destination POI.", error)
        ContextCompat.getMainExecutor(context).execute {
            onSearchFailed(error.message ?: "unknown error")
        }
    }
}

@Suppress("DEPRECATION")
private fun startDeviceHeadingUpdates(
    context: Context,
    onHeadingChanged: (Float) -> Unit
): () -> Unit {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        ?: return {}
    val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: return {}
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    var smoothedHeadingDegrees = Float.NaN

    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val rotationMatrix = FloatArray(9)
            val orientation = FloatArray(3)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)

            val screenRotationDegrees = when (windowManager.defaultDisplay.rotation) {
                Surface.ROTATION_90 -> 90f
                Surface.ROTATION_180 -> 180f
                Surface.ROTATION_270 -> 270f
                else -> 0f
            }
            val headingDegrees = normalizeDegrees(
                Math.toDegrees(orientation[0].toDouble()).toFloat() + screenRotationDegrees
            )
            val headingChange = if (smoothedHeadingDegrees.isNaN()) {
                360f
            } else {
                abs(shortestAngleDifference(smoothedHeadingDegrees, headingDegrees))
            }
            if (headingChange < MIN_HEADING_CHANGE_DEGREES) return

            smoothedHeadingDegrees = if (smoothedHeadingDegrees.isNaN()) {
                headingDegrees
            } else {
                normalizeDegrees(
                    smoothedHeadingDegrees +
                        shortestAngleDifference(smoothedHeadingDegrees, headingDegrees) *
                        HEADING_SMOOTHING_FACTOR
                )
            }
            onHeadingChanged(smoothedHeadingDegrees)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    sensorManager.registerListener(
        listener,
        rotationVectorSensor,
        SensorManager.SENSOR_DELAY_UI
    )
    return { sensorManager.unregisterListener(listener) }
}

private fun shortestAngleDifference(fromDegrees: Float, toDegrees: Float): Float {
    return (toDegrees - fromDegrees + 540f) % 360f - 180f
}

private fun normalizeDegrees(degrees: Float): Float {
    return (degrees % 360f + 360f) % 360f
}

@SuppressLint("MissingPermission")
private fun startCurrentLocationUpdates(
    context: Context,
    onLocationLoaded: (Double, Double, Float, Float) -> Unit,
    onLocationUnavailable: (String) -> Unit
): () -> Unit {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (locationManager == null) {
        onLocationUnavailable("위치 서비스를 사용할 수 없습니다")
        return {}
    }

    val enabledProviders = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    ).filter(locationManager::isProviderEnabled)

    if (enabledProviders.isEmpty()) {
        onLocationUnavailable("활성화된 위치 공급자가 없습니다")
        return {}
    }

    val locationListener = LocationListener { location ->
        onLocationLoaded(
            location.latitude,
            location.longitude,
            location.accuracy,
            location.bearing
        )
    }

    enabledProviders.forEach { provider ->
        runCatching {
            locationManager.requestLocationUpdates(
                provider,
                LOCATION_UPDATE_INTERVAL_MILLIS,
                LOCATION_UPDATE_DISTANCE_METERS,
                locationListener,
                Looper.getMainLooper()
            )
        }.onFailure { error ->
            Log.w(TMAP_LOG_TAG, "Failed to subscribe to $provider location updates.", error)
        }
    }

    return {
        locationManager.removeUpdates(locationListener)
    }
}

private fun fetchCurrentLocation(
    context: Context,
    onLocationLoaded: (Double, Double, Float, Float) -> Unit,
    onLocationUnavailable: (String) -> Unit
) {
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasPermission) {
        onLocationUnavailable("위치 권한이 없습니다")
        return
    }

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (locationManager == null) {
        onLocationUnavailable("위치 서비스를 사용할 수 없습니다")
        return
    }

    val provider = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    if (provider == null) {
        fallbackToLastKnownLocation(locationManager, onLocationLoaded, onLocationUnavailable)
        return
    }

    val cancellationSignal = CancellationSignal()
    LocationManagerCompat.getCurrentLocation(
        locationManager,
        provider,
        cancellationSignal,
        ContextCompat.getMainExecutor(context)
    ) { location ->
        if (location != null) {
            onLocationLoaded(
                location.latitude,
                location.longitude,
                location.accuracy,
                location.bearing
            )
        } else {
            fallbackToLastKnownLocation(locationManager, onLocationLoaded, onLocationUnavailable)
        }
    }
}

@SuppressLint("MissingPermission")
private fun fallbackToLastKnownLocation(
    locationManager: LocationManager,
    onLocationLoaded: (Double, Double, Float, Float) -> Unit,
    onLocationUnavailable: (String) -> Unit
) {
    val lastKnownLocation = sequenceOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull(Location::getTime)

    if (lastKnownLocation != null) {
        onLocationLoaded(
            lastKnownLocation.latitude,
            lastKnownLocation.longitude,
            lastKnownLocation.accuracy,
            lastKnownLocation.bearing
        )
    } else {
        onLocationUnavailable("아직 위치를 확인하지 못했습니다")
    }
}
