package com.bycho.safereturnhome.ui.screen.map

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
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
import android.telephony.SmsManager
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.bycho.safereturnhome.data.GuardianPreferences
import com.bycho.safereturnhome.data.RecentDestinationPreferences
import com.bycho.safereturnhome.data.guardianPhoneNumberError
import com.bycho.safereturnhome.ui.state.DestinationSearchResult
import com.bycho.safereturnhome.ui.state.MapUiState
import com.bycho.safereturnhome.ui.viewmodel.MapViewModel
import com.skt.tmap.TMapData
import com.skt.tmap.TMapPoint
import com.skt.tmap.TMapView
import com.skt.tmap.overlay.TMapCircle
import com.skt.tmap.overlay.TMapMarkerItem
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import org.w3c.dom.Element

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
private const val GUIDANCE_STEP_REACHED_THRESHOLD_METERS = 18f
private const val GUIDANCE_EARLY_ANNOUNCEMENT_METERS = 100
private const val GUIDANCE_NEAR_ANNOUNCEMENT_METERS = 30
private const val GUIDANCE_TURN_REFERENCE_DISTANCE_METERS = 18f
private const val GUIDANCE_MIN_TURN_DEGREES = 35f
private const val GUIDANCE_MIN_STEP_SPACING_METERS = 25f
private const val SOS_COUNTDOWN_SECONDS = 5
private const val SOS_HOLD_DURATION_MILLIS = 3_000L
private const val SOS_SMS_SENT_ACTION = "com.bycho.safereturnhome.SOS_SMS_SENT"
private const val SOS_SMS_REQUEST_ID_KEY = "sos-sms-request-id"

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

private data class RouteGuidanceStep(
    val instruction: String,
    val maneuver: String,
    val latitude: Double,
    val longitude: Double
)

private data class SmsSendRequest(
    val requestId: Int,
    val partCount: Int
)

@Composable
fun MapRoute(
    onBackClick: () -> Unit,
    onNavigationFinished: () -> Unit,
    viewModel: MapViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    MapScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onNavigationFinished = onNavigationFinished,
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
    onNavigationFinished: () -> Unit,
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
    val guardianPreferences = remember(context) { GuardianPreferences(context) }
    val recentDestinationPreferences = remember(context) { RecentDestinationPreferences(context) }
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
    var routeGuidanceSteps by remember { mutableStateOf<List<RouteGuidanceStep>>(emptyList()) }
    var currentGuidanceStepIndex by remember { mutableIntStateOf(0) }
    var announcedGuidanceStepIndex by remember { mutableIntStateOf(-1) }
    var announcedGuidanceThresholdMeters by remember { mutableIntStateOf(Int.MAX_VALUE) }
    var consecutiveRouteDeviationCount by remember { mutableIntStateOf(0) }
    var isRouteRecalculationInProgress by remember { mutableStateOf(false) }
    var hasArrivedAtDestination by remember { mutableStateOf(false) }
    var isArrivalNoticeVisible by remember { mutableStateOf(false) }
    var isNavigationEndConfirmationVisible by remember { mutableStateOf(false) }
    var isUpcomingGuidanceExpanded by remember { mutableStateOf(false) }
    var savedGuardianPhoneNumber by remember {
        mutableStateOf(guardianPreferences.getPhoneNumber())
    }
    var sosCountdownSeconds by remember { mutableStateOf<Int?>(null) }
    var sosNoticeMessage by remember { mutableStateOf<String?>(null) }
    var activeSmsRequest by remember { mutableStateOf<SmsSendRequest?>(null) }
    var sentSmsPartCount by remember { mutableIntStateOf(0) }
    val sendSosMessage: () -> Unit = {
        val sendRequest = sendGuardianSms(
            context = context,
            guardianPhoneNumber = savedGuardianPhoneNumber,
            latitude = uiState.currentLatitude,
            longitude = uiState.currentLongitude
        )
        activeSmsRequest = sendRequest
        sentSmsPartCount = 0
        sosNoticeMessage = if (sendRequest != null) {
            "SOS 문자 전송을 요청했습니다"
        } else {
            "SOS 문자 전송 요청에 실패했습니다"
        }
    }
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
                    isArrivalNoticeVisible = true
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
            if (routeGuidanceSteps.isNotEmpty() && !hasArrivedAtDestination) {
                var guidanceStepIndex = currentGuidanceStepIndex.coerceAtMost(routeGuidanceSteps.lastIndex)
                var guidanceStep = routeGuidanceSteps[guidanceStepIndex]
                var distanceToGuidanceStep = calculateDistanceMeters(
                    latitude,
                    longitude,
                    guidanceStep.latitude,
                    guidanceStep.longitude
                )
                while (
                    distanceToGuidanceStep <= GUIDANCE_STEP_REACHED_THRESHOLD_METERS &&
                    guidanceStepIndex < routeGuidanceSteps.lastIndex
                ) {
                    guidanceStepIndex += 1
                    guidanceStep = routeGuidanceSteps[guidanceStepIndex]
                    distanceToGuidanceStep = calculateDistanceMeters(
                        latitude,
                        longitude,
                        guidanceStep.latitude,
                        guidanceStep.longitude
                    )
                }
                if (guidanceStepIndex != currentGuidanceStepIndex) {
                    currentGuidanceStepIndex = guidanceStepIndex
                    announcedGuidanceStepIndex = -1
                    announcedGuidanceThresholdMeters = Int.MAX_VALUE
                }
                val announcementThresholdMeters = when {
                    distanceToGuidanceStep <= GUIDANCE_NEAR_ANNOUNCEMENT_METERS ->
                        GUIDANCE_NEAR_ANNOUNCEMENT_METERS
                    distanceToGuidanceStep <= GUIDANCE_EARLY_ANNOUNCEMENT_METERS ->
                        GUIDANCE_EARLY_ANNOUNCEMENT_METERS
                    else -> null
                }
                if (
                    announcementThresholdMeters != null &&
                    (announcedGuidanceStepIndex != guidanceStepIndex ||
                        announcedGuidanceThresholdMeters != announcementThresholdMeters)
                ) {
                    speak(
                        textToSpeech,
                        "${distanceToGuidanceStep.toInt()}미터 앞에서 ${guidanceStep.instruction}"
                    )
                    announcedGuidanceStepIndex = guidanceStepIndex
                    announcedGuidanceThresholdMeters = announcementThresholdMeters
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
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            sosCountdownSeconds = SOS_COUNTDOWN_SECONDS
        } else {
            sosNoticeMessage = "보호자에게 SOS 문자를 보내려면 문자 권한이 필요합니다"
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

    LaunchedEffect(sosCountdownSeconds) {
        val seconds = sosCountdownSeconds ?: return@LaunchedEffect
        if (seconds > 0) {
            delay(1_000L)
            sosCountdownSeconds = seconds - 1
        } else {
            sendSosMessage()
            sosCountdownSeconds = null
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
        val smsSentReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val request = activeSmsRequest ?: return
                if (intent?.getIntExtra(SOS_SMS_REQUEST_ID_KEY, -1) != request.requestId) return

                if (resultCode == Activity.RESULT_OK) {
                    sentSmsPartCount += 1
                    if (sentSmsPartCount >= request.partCount) {
                        activeSmsRequest = null
                        sosNoticeMessage = "보호자에게 SOS 문자를 전송했습니다"
                    }
                } else {
                    activeSmsRequest = null
                    sosNoticeMessage = smsSendFailureMessage(resultCode)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            smsSentReceiver,
            IntentFilter(SOS_SMS_SENT_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(smsSentReceiver) }
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
        },
        floatingActionButton = {
            SosFloatingActionButton(
                onSosActivated = {
                    if (guardianPhoneNumberError(savedGuardianPhoneNumber) != null) {
                        sosNoticeMessage = "보호자 설정에서 올바른 휴대폰 번호를 먼저 저장해주세요"
                    } else if (
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.SEND_SMS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                    } else {
                        vibrate(context, longArrayOf(0, 120, 80, 120))
                        sosCountdownSeconds = SOS_COUNTDOWN_SECONDS
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
                            routeGuidanceSteps = emptyList()
                            currentGuidanceStepIndex = 0
                            announcedGuidanceStepIndex = -1
                            announcedGuidanceThresholdMeters = Int.MAX_VALUE
                            destinationFocusRequestId += 1
                            recentDestinationPreferences.saveRecentDestination(
                                name = result.name,
                                address = result.address,
                                latitude = result.latitude,
                                longitude = result.longitude
                            )
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
            if (isNavigationMode) {
                val guidanceStep = routeGuidanceSteps.getOrNull(currentGuidanceStepIndex)
                val distanceToGuidanceStep = if (
                    guidanceStep != null &&
                    uiState.currentLatitude != null &&
                    uiState.currentLongitude != null
                ) {
                    calculateDistanceMeters(
                        startLatitude = uiState.currentLatitude,
                        startLongitude = uiState.currentLongitude,
                        endLatitude = guidanceStep.latitude,
                        endLongitude = guidanceStep.longitude
                    ).toInt()
                } else {
                    null
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = guidanceStep?.maneuver?.ifBlank { "다음 안내" }
                                ?: "경로 안내 준비 중",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (
                            guidanceStep != null &&
                            guidanceStep.instruction.isNotBlank() &&
                            guidanceStep.instruction != guidanceStep.maneuver
                        ) {
                            Text(text = guidanceStep.instruction)
                        }
                        distanceToGuidanceStep?.let { distanceMeters ->
                            Text(text = "${distanceMeters}m 앞")
                        }
                    }
                }
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
                    onRoutePointsChanged = { activeRoutePoints = it },
                    onRouteGuidanceStepsChanged = { steps ->
                        routeGuidanceSteps = steps
                        currentGuidanceStepIndex = 0
                        announcedGuidanceStepIndex = -1
                        announcedGuidanceThresholdMeters = Int.MAX_VALUE
                    }
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                isUpcomingGuidanceExpanded = !isUpcomingGuidanceExpanded
                            }
                        ) {
                            Text(
                                if (isUpcomingGuidanceExpanded) {
                                    "앞으로의 경로 접기"
                                } else {
                                    "앞으로의 경로 펼치기"
                                }
                            )
                        }
                        if (isUpcomingGuidanceExpanded) {
                            val upcomingGuidanceSteps = routeGuidanceSteps
                                .drop(currentGuidanceStepIndex)
                                .take(5)
                            if (upcomingGuidanceSteps.isEmpty()) {
                                Text("표시할 다음 안내가 없습니다")
                            } else {
                                upcomingGuidanceSteps.forEach { guidanceStep ->
                                    val distanceMeters = if (
                                        uiState.currentLatitude != null &&
                                        uiState.currentLongitude != null
                                    ) {
                                        calculateRouteDistanceToGuidanceStepMeters(
                                            currentLatitude = uiState.currentLatitude,
                                            currentLongitude = uiState.currentLongitude,
                                            routePoints = activeRoutePoints,
                                            guidanceStep = guidanceStep
                                        )
                                    } else {
                                        null
                                    }
                                    Text(
                                        text = buildString {
                                            if (distanceMeters != null) {
                                                append("${distanceMeters}m 앞 ")
                                            }
                                            append(guidanceStep.maneuver)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (isNavigationMode) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { isNavigationEndConfirmationVisible = true }
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

    sosCountdownSeconds?.let { seconds ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("SOS 문자 자동 전송 준비") },
            text = {
                Text(
                    if (seconds > 0) {
                        "${seconds}초 후 보호자에게 SOS 문자를 자동으로 전송합니다"
                    } else {
                        "보호자에게 SOS 문자를 전송하는 중입니다"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        sendSosMessage()
                        sosCountdownSeconds = null
                    }
                ) {
                    Text("지금 전송")
                }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { sosCountdownSeconds = null }) {
                        Text("취소")
                    }
                    TextButton(onClick = { openEmergencyDialer(context) }) {
                        Text("112 전화 연결")
                    }
                }
            }
        )
    }

    if (isArrivalNoticeVisible) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("목적지 도착") },
            text = { Text("목적지에 도착했습니다. 안전한 귀가가 완료되었습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        isArrivalNoticeVisible = false
                        onNavigationFinished()
                    }
                ) {
                    Text("홈으로 돌아가기")
                }
            }
        )
    }

    if (isNavigationEndConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { isNavigationEndConfirmationVisible = false },
            title = { Text("경로 안내 종료") },
            text = { Text("현재 경로 안내를 종료하고 홈 화면으로 돌아갈까요?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        isNavigationEndConfirmationVisible = false
                        isNavigationMode = false
                        isNorthUpMode = false
                        isUpcomingGuidanceExpanded = false
                        onNavigationFinished()
                    }
                ) {
                    Text("종료하기")
                }
            },
            dismissButton = {
                TextButton(onClick = { isNavigationEndConfirmationVisible = false }) {
                    Text("계속 안내")
                }
            }
        )
    }

    sosNoticeMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { sosNoticeMessage = null },
            title = { Text("SOS 설정") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { sosNoticeMessage = null }) {
                    Text("확인")
                }
            }
        )
    }
}

@Composable
private fun SosFloatingActionButton(
    onSosActivated: () -> Unit
) {
    FloatingActionButton(
        modifier = Modifier.pointerInput(onSosActivated) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val wasHeldLongEnough = withTimeoutOrNull(SOS_HOLD_DURATION_MILLIS) {
                    while (awaitPointerEvent().changes.any { it.pressed }) {
                        // Wait until the user releases before the hold duration.
                    }
                    false
                } ?: true
                if (wasHeldLongEnough) {
                    onSosActivated()
                    while (awaitPointerEvent().changes.any { it.pressed }) {
                        // Prevent another SOS activation before the current press is released.
                    }
                }
            }
        },
        onClick = {},
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError
    ) {
        Text("SOS\n3초")
    }
}

private fun sendGuardianSms(
    context: Context,
    guardianPhoneNumber: String,
    latitude: Double?,
    longitude: Double?
): SmsSendRequest? {
    val locationMessage = if (latitude != null && longitude != null) {
        "현재 위치: https://maps.google.com/?q=$latitude,$longitude"
    } else {
        "현재 위치를 확인할 수 없습니다"
    }
    val message = "[안전귀가 SOS] 도움이 필요합니다. $locationMessage"
    return runCatching {
        val normalizedPhoneNumber = guardianPhoneNumber.filterIndexed { index, character ->
            character.isDigit() || (character == '+' && index == 0)
        }
        require(normalizedPhoneNumber.isNotBlank()) { "Guardian phone number is blank." }
        val smsManager = context.getSystemService(SmsManager::class.java)
            ?: error("SMS service is unavailable.")
        val messageParts = smsManager.divideMessage(message)
        val requestId = (System.currentTimeMillis() and Int.MAX_VALUE.toLong()).toInt()
        val sentIntents = ArrayList<PendingIntent>(messageParts.size)
        messageParts.indices.forEach { partIndex ->
            val sentIntent = Intent(SOS_SMS_SENT_ACTION)
                .setPackage(context.packageName)
                .putExtra(SOS_SMS_REQUEST_ID_KEY, requestId)
            sentIntents += PendingIntent.getBroadcast(
                context,
                requestId + partIndex,
                sentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        smsManager.sendMultipartTextMessage(
            normalizedPhoneNumber,
            null,
            messageParts,
            sentIntents,
            null
        )
        SmsSendRequest(requestId = requestId, partCount = messageParts.size)
    }.onFailure { error ->
        Log.e(TMAP_LOG_TAG, "Failed to send the guardian SMS.", error)
    }.getOrNull()
}

private fun smsSendFailureMessage(resultCode: Int): String {
    val reason = when (resultCode) {
        SmsManager.RESULT_ERROR_RADIO_OFF -> "휴대폰 통신 기능이 꺼져 있습니다"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "통신 서비스에 연결되지 않았습니다"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "문자 전송 한도를 초과했습니다"
        SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> "발신 제한 설정으로 차단되었습니다"
        else -> "통신사 또는 기기에서 전송을 거절했습니다"
    }
    return "SOS 문자 전송에 실패했습니다: $reason"
}

private fun openEmergencyDialer(context: Context) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure { error ->
        Log.e(TMAP_LOG_TAG, "Failed to open the emergency dialer.", error)
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
    onRoutePointsChanged: (List<TMapPoint>) -> Unit,
    onRouteGuidanceStepsChanged: (List<RouteGuidanceStep>) -> Unit
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
                    onRoutePointsChanged = onRoutePointsChanged,
                    onRouteGuidanceStepsChanged = onRouteGuidanceStepsChanged
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
    onRoutePointsChanged: (List<TMapPoint>) -> Unit,
    onRouteGuidanceStepsChanged: (List<RouteGuidanceStep>) -> Unit
) {
    findRouteGuidanceSteps(
        view = view,
        startPoint = startPoint,
        destinationPoint = destinationPoint,
        onRouteGuidanceStepsChanged = onRouteGuidanceStepsChanged
    )
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
                            val fallbackGuidanceSteps =
                                deriveRouteGuidanceSteps(polyLine.linePointList)
                            Log.i(
                                TMAP_LOG_TAG,
                                "Generated ${fallbackGuidanceSteps.size} guidance steps " +
                                    "from the pedestrian route polyline."
                            )
                            onRouteGuidanceStepsChanged(fallbackGuidanceSteps)
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

private fun findRouteGuidanceSteps(
    view: TMapView,
    startPoint: TMapPoint,
    destinationPoint: TMapPoint,
    onRouteGuidanceStepsChanged: (List<RouteGuidanceStep>) -> Unit
) {
    view.post {
        onRouteGuidanceStepsChanged(emptyList())
    }
    Log.i(TMAP_LOG_TAG, "Requesting turn-by-turn guidance.")
    runCatching {
        TMapData().findPathDataAllType(
            TMapData.TMapPathType.PEDESTRIAN_PATH,
            startPoint,
            destinationPoint,
            object : TMapData.OnFindPathDataAllTypeListener {
                override fun onFindPathDataAllType(document: org.w3c.dom.Document?) {
                    if (document == null) {
                        Log.w(TMAP_LOG_TAG, "Turn-by-turn guidance response document is null.")
                    }
                    val guidanceSteps = document?.let(::parseRouteGuidanceSteps).orEmpty()
                    if (guidanceSteps.isEmpty() && document != null) {
                        Log.w(
                            TMAP_LOG_TAG,
                            "Loaded 0 turn-by-turn guidance steps. " +
                                summarizeRouteGuidanceDocument(document)
                        )
                    } else {
                        Log.i(
                            TMAP_LOG_TAG,
                            "Loaded ${guidanceSteps.size} turn-by-turn guidance steps."
                        )
                    }
                    if (guidanceSteps.isNotEmpty()) {
                        view.post {
                            onRouteGuidanceStepsChanged(guidanceSteps)
                        }
                    }
                }
            }
        )
    }.onFailure { error ->
        Log.w(TMAP_LOG_TAG, "Failed to load turn-by-turn guidance.", error)
    }
}

private fun deriveRouteGuidanceSteps(routePoints: List<TMapPoint>): List<RouteGuidanceStep> {
    if (routePoints.isEmpty()) return emptyList()

    val guidanceSteps = mutableListOf<RouteGuidanceStep>()
    var lastGuidancePoint = routePoints.first()
    for (index in 1 until routePoints.lastIndex) {
        val turnPoint = routePoints[index]
        val incomingPoint = findRouteReferencePoint(routePoints, index, -1)
        val outgoingPoint = findRouteReferencePoint(routePoints, index, 1)
        if (incomingPoint == turnPoint || outgoingPoint == turnPoint) continue

        val incomingBearing = calculateBearingDegrees(incomingPoint, turnPoint)
        val outgoingBearing = calculateBearingDegrees(turnPoint, outgoingPoint)
        val turnDegrees = shortestAngleDifference(incomingBearing, outgoingBearing)
        if (
            abs(turnDegrees) < GUIDANCE_MIN_TURN_DEGREES ||
            calculateDistanceMeters(
                lastGuidancePoint.latitude,
                lastGuidancePoint.longitude,
                turnPoint.latitude,
                turnPoint.longitude
            ) < GUIDANCE_MIN_STEP_SPACING_METERS
        ) {
            continue
        }

        val maneuver = if (turnDegrees > 0f) "우회전하세요" else "좌회전하세요"
        guidanceSteps += RouteGuidanceStep(
            instruction = maneuver,
            maneuver = maneuver,
            latitude = turnPoint.latitude,
            longitude = turnPoint.longitude
        )
        lastGuidancePoint = turnPoint
    }

    val destinationPoint = routePoints.last()
    guidanceSteps += RouteGuidanceStep(
        instruction = "목적지에 도착합니다",
        maneuver = "목적지 도착",
        latitude = destinationPoint.latitude,
        longitude = destinationPoint.longitude
    )
    return guidanceSteps
}

private fun findRouteReferencePoint(
    routePoints: List<TMapPoint>,
    pivotIndex: Int,
    direction: Int
): TMapPoint {
    val pivotPoint = routePoints[pivotIndex]
    var index = pivotIndex + direction
    while (index in routePoints.indices) {
        val candidatePoint = routePoints[index]
        if (
            calculateDistanceMeters(
                pivotPoint.latitude,
                pivotPoint.longitude,
                candidatePoint.latitude,
                candidatePoint.longitude
            ) >= GUIDANCE_TURN_REFERENCE_DISTANCE_METERS
        ) {
            return candidatePoint
        }
        index += direction
    }
    return pivotPoint
}

private fun calculateBearingDegrees(startPoint: TMapPoint, endPoint: TMapPoint): Float {
    val results = FloatArray(3)
    Location.distanceBetween(
        startPoint.latitude,
        startPoint.longitude,
        endPoint.latitude,
        endPoint.longitude,
        results
    )
    return normalizeDegrees(results[1])
}

private fun summarizeRouteGuidanceDocument(document: org.w3c.dom.Document): String {
    val nodes = document.getElementsByTagName("*")
    val tagNames = buildList {
        for (index in 0 until nodes.length) {
            add(nodes.item(index).nodeName)
        }
    }.distinct()
        .take(30)
        .joinToString()
    return "root=${document.documentElement?.nodeName}, " +
        "elementCount=${nodes.length}, tags=[$tagNames]"
}

private fun parseRouteGuidanceSteps(document: org.w3c.dom.Document): List<RouteGuidanceStep> {
    val featureMembers = document.findDescendantElements("featureMember")
    return buildList {
        for (featureMember in featureMembers) {
            val coordinateText = featureMember.findFirstDescendantText("coordinates") ?: continue
            val coordinates = coordinateText.trim().split(",")
            val longitude = coordinates.getOrNull(0)?.trim()?.toDoubleOrNull() ?: continue
            val latitude = coordinates.getOrNull(1)?.trim()?.toDoubleOrNull() ?: continue
            val description = featureMember.findFirstDescendantText("description")
                ?.trim()
                .orEmpty()
            val turnType = featureMember.findFirstDescendantText("turnType")
                ?.trim()
                ?.toIntOrNull()
            val pointType = featureMember.findFirstDescendantText("pointType")
                ?.trim()
                .orEmpty()
            if (turnType == null && pointType.isBlank()) continue
            val maneuver = maneuverLabel(turnType, pointType)
            val instruction = description.ifBlank { maneuver }
            if (instruction.isBlank()) continue

            add(
                RouteGuidanceStep(
                    instruction = instruction,
                    maneuver = maneuver,
                    latitude = latitude,
                    longitude = longitude
                )
            )
        }
    }.distinctBy { "${it.latitude},${it.longitude}:${it.instruction}" }
}

private fun org.w3c.dom.Document.findDescendantElements(localName: String): List<Element> {
    val namespacedNodes = getElementsByTagNameNS("*", localName)
    if (namespacedNodes.length > 0) {
        return buildList {
            for (index in 0 until namespacedNodes.length) {
                (namespacedNodes.item(index) as? Element)?.let(::add)
            }
        }
    }

    val fallbackNodes = getElementsByTagName("*")
    return buildList {
        for (index in 0 until fallbackNodes.length) {
            val node = fallbackNodes.item(index)
            if (node.nodeName.substringAfter(":") == localName) {
                (node as? Element)?.let(::add)
            }
        }
    }
}

private fun Element.findFirstDescendantText(localName: String): String? {
    val nodes = getElementsByTagNameNS("*", localName)
    if (nodes.length > 0) return nodes.item(0)?.textContent

    val fallbackNodes = getElementsByTagName("*")
    for (index in 0 until fallbackNodes.length) {
        val node = fallbackNodes.item(index)
        if (node.nodeName.substringAfter(":") == localName) return node.textContent
    }
    return null
}

private fun maneuverLabel(turnType: Int?, pointType: String): String {
    return when (turnType) {
        11 -> "직진하세요"
        12 -> "좌회전하세요"
        13 -> "우회전하세요"
        14 -> "유턴하세요"
        16, 17 -> "왼쪽 방향으로 이동하세요"
        18, 19 -> "오른쪽 방향으로 이동하세요"
        125 -> "육교를 이용하세요"
        126 -> "지하보도를 이용하세요"
        127 -> "계단을 이용하세요"
        211 -> "횡단보도를 건너세요"
        212 -> "좌측 횡단보도를 건너세요"
        213 -> "우측 횡단보도를 건너세요"
        else -> when {
            pointType.startsWith("SP") -> "경로 안내를 시작합니다"
            pointType.startsWith("EP") -> "목적지에 도착합니다"
            else -> ""
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

private fun calculateRouteDistanceToGuidanceStepMeters(
    currentLatitude: Double,
    currentLongitude: Double,
    routePoints: List<TMapPoint>,
    guidanceStep: RouteGuidanceStep
): Int? {
    if (routePoints.isEmpty()) return null

    val currentRoutePointIndex = findNearestRoutePointIndex(
        latitude = currentLatitude,
        longitude = currentLongitude,
        routePoints = routePoints
    ) ?: return null
    val guidanceRoutePointIndex = findNearestRoutePointIndex(
        latitude = guidanceStep.latitude,
        longitude = guidanceStep.longitude,
        routePoints = routePoints
    ) ?: return null
    if (guidanceRoutePointIndex <= currentRoutePointIndex) return 0

    val distanceToCurrentRoutePoint = calculateDistanceMeters(
        startLatitude = currentLatitude,
        startLongitude = currentLongitude,
        endLatitude = routePoints[currentRoutePointIndex].latitude,
        endLongitude = routePoints[currentRoutePointIndex].longitude
    )
    val routeDistanceMeters = routePoints
        .subList(currentRoutePointIndex, guidanceRoutePointIndex + 1)
        .zipWithNext()
        .sumOf { (startPoint, endPoint) ->
            calculateDistanceMeters(
                startLatitude = startPoint.latitude,
                startLongitude = startPoint.longitude,
                endLatitude = endPoint.latitude,
                endLongitude = endPoint.longitude
            ).toDouble()
        }
    return (distanceToCurrentRoutePoint + routeDistanceMeters).toInt()
}

private fun findNearestRoutePointIndex(
    latitude: Double,
    longitude: Double,
    routePoints: List<TMapPoint>
): Int? {
    return routePoints.indices.minByOrNull { index ->
        val routePoint = routePoints[index]
        calculateDistanceMeters(
            startLatitude = latitude,
            startLongitude = longitude,
            endLatitude = routePoint.latitude,
            endLongitude = routePoint.longitude
        )
    }
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
