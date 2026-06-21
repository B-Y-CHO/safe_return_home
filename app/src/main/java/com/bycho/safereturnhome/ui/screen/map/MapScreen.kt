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
import android.os.Handler
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.bycho.safereturnhome.data.CctvCoordinate
import com.bycho.safereturnhome.data.CctvRepository
import com.bycho.safereturnhome.data.CctvSafeRoutePlanner
import com.bycho.safereturnhome.data.GuardianPreferences
import com.bycho.safereturnhome.data.RecentDestinationPreferences
import com.bycho.safereturnhome.data.StreetlightCoordinate
import com.bycho.safereturnhome.data.StreetlightRepository
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
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
private const val ROUTE_CCTV_MARKER_ID_PREFIX = "route-cctv-"
private const val ROUTE_STREETLIGHT_MARKER_ID_PREFIX = "route-streetlight-"
private const val LOCATION_UPDATE_INTERVAL_MILLIS = 2_500L
private const val LOCATION_UPDATE_DISTANCE_METERS = 3f
private const val CURRENT_LOCATION_MARKER_MIN_MOVE_METERS = 1f
private const val CURRENT_LOCATION_ACCURACY_MIN_MOVE_METERS = 2f
private const val CURRENT_LOCATION_ACCURACY_MIN_CHANGE_METERS = 3f
private const val CURRENT_LOCATION_BEARING_BUCKET_DEGREES = 5
private const val ROUTE_DEVIATION_THRESHOLD_METERS = 50f
private const val ROUTE_DEVIATION_CONFIRMATION_COUNT = 3
private const val DESTINATION_ARRIVAL_THRESHOLD_METERS = 20f
private const val GUIDANCE_STEP_REACHED_THRESHOLD_METERS = 18f
private const val GUIDANCE_EARLY_ANNOUNCEMENT_METERS = 100
private const val GUIDANCE_NEAR_ANNOUNCEMENT_METERS = 30
private const val GUIDANCE_TURN_REFERENCE_DISTANCE_METERS = 18f
private const val GUIDANCE_MIN_TURN_DEGREES = 35f
private const val GUIDANCE_MIN_STEP_SPACING_METERS = 25f
private const val MAX_CCTV_WAYPOINT_COUNT = 3
private const val MIN_RELEVANT_CCTV_COORDINATE_COUNT_FOR_A_STAR = 12
private const val ROUTE_CCTV_MARKER_RADIUS_METERS = 180f
private const val ROUTE_CCTV_MARKER_CLEAR_LIMIT = 1_000
private const val ROUTE_STREETLIGHT_MARKER_RADIUS_METERS = 180f
private const val ROUTE_STREETLIGHT_MARKER_CLEAR_LIMIT = 2_000
private const val METERS_PER_LATITUDE_DEGREE = 111_320.0
private const val SOS_COUNTDOWN_SECONDS = 5
private const val SOS_HOLD_DURATION_MILLIS = 3_000L
private const val GUMI_CENTER_LATITUDE = 36.1195
private const val GUMI_CENTER_LONGITUDE = 128.3446
private const val GUMI_PRIORITY_RADIUS_METERS = 35_000f
private const val SOS_SMS_SENT_ACTION = "com.bycho.safereturnhome.SOS_SMS_SENT"
private const val SOS_SMS_REQUEST_ID_KEY = "sos-sms-request-id"

private enum class RouteMode {
    GENERAL,
    CCTV_SAFE
}

private data class RouteOptionPreview(
    val distanceMeters: Int? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val waypointCount: Int? = null,
    val candidateCount: Int? = null
)

private data class RouteDistancePreview(
    val destination: DestinationSearchResult,
    val general: RouteOptionPreview,
    val cctvSafe: RouteOptionPreview
)

private data class CctvSafeRouteDistancePreview(
    val distanceMeters: Int?,
    val waypointCount: Int,
    val candidateCctvCount: Int
)

private class MapRenderState {
    var centeredLatitude: Double? = null
    var centeredLongitude: Double? = null
    var recenterRequestId: Int = -1
    var locationPointLatitude: Double? = null
    var locationPointLongitude: Double? = null
    var currentMarkerLatitude: Double? = null
    var currentMarkerLongitude: Double? = null
    var currentMarkerBearingDegrees: Int? = null
    private var currentLocationIconBearingDegrees: Int? = null
    private var currentLocationIcon: Bitmap? = null
    var accuracyLatitude: Double? = null
    var accuracyLongitude: Double? = null
    var accuracyMeters: Float? = null
    var destination: DestinationSearchResult? = null
    var hasCurrentLocationMarker: Boolean = false
    var routeSearchRequestId: Int = 0

    fun currentLocationIconFor(bearingDegrees: Int): Bitmap {
        if (currentLocationIconBearingDegrees != bearingDegrees || currentLocationIcon == null) {
            currentLocationIcon = createCurrentLocationIcon(bearingDegrees.toFloat())
            currentLocationIconBearingDegrees = bearingDegrees
        }
        return currentLocationIcon!!
    }
}

private data class RemainingRouteMetrics(
    val remainingDistanceMeters: Int,
    val distanceFromRouteMeters: Float
)

private data class CctvSafeRouteAnalysis(
    val generalDistanceMeters: Int?,
    val safeDistanceMeters: Int?,
    val candidateCctvCount: Int,
    val selectedWaypointCount: Int,
    val routeCctvCount: Int?,
    val routeStreetlightLampCount: Int?,
    val routeStreetlightLocationCount: Int?,
    val estimatedCoverageRatio: Double,
    val estimatedStreetlightCoverageRatio: Double?
)

private fun CctvSafeRouteAnalysis.mergeWith(
    update: CctvSafeRouteAnalysis
): CctvSafeRouteAnalysis {
    return update.copy(
        routeCctvCount = update.routeCctvCount ?: routeCctvCount,
        routeStreetlightLampCount = update.routeStreetlightLampCount ?: routeStreetlightLampCount,
        routeStreetlightLocationCount = update.routeStreetlightLocationCount ?: routeStreetlightLocationCount
    )
}

private data class FlatPoint(
    val x: Double,
    val y: Double
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
        onRouteSearchProgress = viewModel::onRouteSearchProgress,
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
    onRouteSearchProgress: (String) -> Unit,
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
    var selectedRouteMode by remember { mutableStateOf(RouteMode.GENERAL) }
    var isFollowingCurrentLocation by remember { mutableStateOf(true) }
    var isMapTouchInProgress by remember { mutableStateOf(false) }
    var isNavigationMode by remember { mutableStateOf(false) }
    var tMapView by remember { mutableStateOf<TMapView?>(null) }
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    var activeRoutePoints by remember { mutableStateOf<List<TMapPoint>>(emptyList()) }
    var activeCctvWaypointCount by remember { mutableIntStateOf(0) }
    var activeCctvRouteAnalysis by remember { mutableStateOf<CctvSafeRouteAnalysis?>(null) }
    var routeDistancePreview by remember { mutableStateOf<RouteDistancePreview?>(null) }
    var routeDistancePreviewRequestId by remember { mutableIntStateOf(0) }
    var routeDistancePreviewStartLatitude by remember { mutableStateOf<Double?>(null) }
    var routeDistancePreviewStartLongitude by remember { mutableStateOf<Double?>(null) }
    var routeGuidanceSteps by remember { mutableStateOf<List<RouteGuidanceStep>>(emptyList()) }
    var currentGuidanceStepIndex by remember { mutableIntStateOf(0) }
    var announcedGuidanceStepIndex by remember { mutableIntStateOf(-1) }
    var announcedGuidanceThresholdMeters by remember { mutableIntStateOf(Int.MAX_VALUE) }
    var consecutiveRouteDeviationCount by remember { mutableIntStateOf(0) }
    var isRouteRecalculationInProgress by remember { mutableStateOf(false) }
    var hasArrivedAtDestination by remember { mutableStateOf(false) }
    var isArrivalNoticeVisible by remember { mutableStateOf(false) }
    var isNavigationEndConfirmationVisible by remember { mutableStateOf(false) }
    var savedGuardianPhoneNumber by remember {
        mutableStateOf(guardianPreferences.getSosPhoneNumber())
    }
    val isSavedGuardianPhoneNumberVerified by remember {
        mutableStateOf(guardianPreferences.isPhoneNumberVerified())
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

    val onSosActivated: () -> Unit = {
        if (guardianPhoneNumberError(savedGuardianPhoneNumber) != null) {
            sosNoticeMessage = "보호자 설정에서 올바른 휴대폰 번호 인증을 먼저 완료해주세요"
        } else if (!isSavedGuardianPhoneNumberVerified) {
            sosNoticeMessage = "보호자 설정에서 휴대폰 번호 인증을 먼저 완료해주세요"
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

    fun updateRouteDistancePreview(
        requestId: Int,
        update: (RouteDistancePreview) -> RouteDistancePreview
    ) {
        if (routeDistancePreviewRequestId != requestId) return
        routeDistancePreview = routeDistancePreview?.let(update)
    }

    fun requestRouteDistancePreview(
        destination: DestinationSearchResult,
        latitude: Double,
        longitude: Double,
        view: TMapView
    ) {
        val requestId = routeDistancePreviewRequestId + 1
        routeDistancePreviewRequestId = requestId
        routeDistancePreviewStartLatitude = latitude
        routeDistancePreviewStartLongitude = longitude
        routeDistancePreview = RouteDistancePreview(
            destination = destination,
            general = RouteOptionPreview(
                isLoading = true,
                statusMessage = "일반 경로 거리를 계산하는 중입니다."
            ),
            cctvSafe = RouteOptionPreview(
                isLoading = true,
                statusMessage = "CCTV·가로등 경로 거리를 계산하는 중입니다."
            )
        )

        val startPoint = TMapPoint(latitude, longitude)
        val destinationPoint = TMapPoint(destination.latitude, destination.longitude)
        findPedestrianRouteDistance(
            view = view,
            startPoint = startPoint,
            destinationPoint = destinationPoint
        ) { generalDistanceMeters ->
            updateRouteDistancePreview(requestId) { preview ->
                preview.copy(
                    general = RouteOptionPreview(
                        distanceMeters = generalDistanceMeters,
                        errorMessage = if (generalDistanceMeters == null) {
                            "일반 경로 거리를 계산하지 못했습니다."
                        } else {
                            null
                        }
                    )
                )
            }
            if (routeDistancePreviewRequestId != requestId) return@findPedestrianRouteDistance

            findCctvSafeRouteDistancePreview(
                view = view,
                startPoint = startPoint,
                destinationPoint = destinationPoint,
                destinationAddress = destination.address,
                generalDistanceMeters = generalDistanceMeters,
                onProgress = { message ->
                    updateRouteDistancePreview(requestId) { preview ->
                        preview.copy(
                            cctvSafe = preview.cctvSafe.copy(
                                isLoading = true,
                                statusMessage = message,
                                errorMessage = null
                            )
                        )
                    }
                },
                onCompleted = { previewResult ->
                    updateRouteDistancePreview(requestId) { preview ->
                        preview.copy(
                            cctvSafe = RouteOptionPreview(
                                distanceMeters = previewResult.distanceMeters,
                                errorMessage = if (previewResult.distanceMeters == null) {
                                    "CCTV·가로등 경로 거리를 계산하지 못했습니다."
                                } else {
                                    null
                                },
                                waypointCount = previewResult.waypointCount,
                                candidateCount = previewResult.candidateCctvCount
                            )
                        )
                    }
                },
                onFailed = { reason ->
                    updateRouteDistancePreview(requestId) { preview ->
                        preview.copy(
                            cctvSafe = RouteOptionPreview(
                                errorMessage = reason
                            )
                        )
                    }
                }
            )
        }
    }

    LaunchedEffect(
        uiState.selectedDestination,
        uiState.currentLatitude,
        uiState.currentLongitude,
        tMapView,
        isNavigationMode
    ) {
        if (isNavigationMode) return@LaunchedEffect
        val destination = uiState.selectedDestination
        if (destination == null) {
            routeDistancePreview = null
            routeDistancePreviewStartLatitude = null
            routeDistancePreviewStartLongitude = null
            return@LaunchedEffect
        }
        val latitude = uiState.currentLatitude ?: return@LaunchedEffect
        val longitude = uiState.currentLongitude ?: return@LaunchedEffect
        val view = tMapView ?: return@LaunchedEffect
        val previewStartLatitude = routeDistancePreviewStartLatitude
        val previewStartLongitude = routeDistancePreviewStartLongitude
        val hasMovedFromPreviewStart = if (
            previewStartLatitude != null &&
            previewStartLongitude != null
        ) {
            calculateDistanceMeters(
                startLatitude = previewStartLatitude,
                startLongitude = previewStartLongitude,
                endLatitude = latitude,
                endLongitude = longitude
            ) >= 50.0
        } else {
            true
        }
        if (
            routeDistancePreview?.destination != destination ||
            hasMovedFromPreviewStart
        ) {
            requestRouteDistancePreview(
                destination = destination,
                latitude = latitude,
                longitude = longitude,
                view = view
            )
        }
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
                .padding(if (isNavigationMode) 0.dp else 24.dp)
                .verticalScroll(
                    state = rememberScrollState(),
                    enabled = !isNavigationMode && !isMapTouchInProgress
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isNavigationMode) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.destinationQuery,
                    onValueChange = onDestinationQueryChanged,
                    label = { Text("목적지 검색") },
                    placeholder = { Text("구미시 장소명 또는 주소") },
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
                            selectedRouteMode = RouteMode.GENERAL
                            hasArrivedAtDestination = false
                            isRouteRecalculationInProgress = false
                            consecutiveRouteDeviationCount = 0
                            activeRoutePoints = emptyList()
                            activeCctvWaypointCount = 0
                            activeCctvRouteAnalysis = null
                            routeDistancePreview = null
                            routeDistancePreviewStartLatitude = null
                            routeDistancePreviewStartLongitude = null
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isNavigationMode) {
                            Modifier.weight(1f)
                        } else {
                            Modifier.height(280.dp)
                        }
                    )
                    .padding(bottom = if (isNavigationMode) 0.dp else 4.dp)
            ) {
                val currentLocationMarkerBearingDegrees = uiState.currentLocationBearingDegrees
                val recenterToCurrentLocation = {
                    isFollowingCurrentLocation = true
                    recenterRequestId += 1
                    val latitude = uiState.currentLatitude
                    val longitude = uiState.currentLongitude
                    if (latitude != null && longitude != null) {
                        tMapView?.setZoomLevel(17)
                        tMapView?.setCenterPoint(latitude, longitude)
                    }
                }
                TMapViewContainer(
                    modifier = Modifier.fillMaxSize(),
                    apiKey = if (uiState.hasApiKey) BuildConfig.TMAP_API_KEY else "",
                    hasApiKey = uiState.hasApiKey,
                    latitude = uiState.currentLatitude,
                    longitude = uiState.currentLongitude,
                    accuracyMeters = uiState.currentLocationAccuracyMeters,
                    bearingDegrees = currentLocationMarkerBearingDegrees,
                    recenterRequestId = recenterRequestId,
                    destination = uiState.selectedDestination,
                    destinationFocusRequestId = destinationFocusRequestId,
                    routeSearchRequestId = routeSearchRequestId,
                    routeMode = selectedRouteMode,
                    isFollowingCurrentLocation = isFollowingCurrentLocation,
                    onMapInteraction = { isFollowingCurrentLocation = false },
                    onMapTouchStateChanged = { isMapTouchInProgress = it },
                    onMapReady = onMapReady,
                    onApiKeyFailed = onApiKeyFailed,
                    onMapViewCreated = { tMapView = it },
                    onRouteSearchCompleted = { distanceMeters ->
                        val isFirstRouteSearch = !isNavigationMode
                        isRouteRecalculationInProgress = false
                        if (selectedRouteMode == RouteMode.GENERAL) {
                            activeCctvWaypointCount = 0
                            activeCctvRouteAnalysis = null
                        }
                        onRouteSearchCompleted(distanceMeters)
                        isNavigationMode = true
                        if (isFirstRouteSearch) {
                            speak(textToSpeech, "경로 안내를 시작합니다.")
                            vibrateNavigationStarted(context)
                        }
                    },
                    onRouteSearchProgress = onRouteSearchProgress,
                    onCctvWaypointCountChanged = { activeCctvWaypointCount = it },
                    onCctvRouteAnalysisChanged = { analysis ->
                        activeCctvRouteAnalysis = if (analysis == null) {
                            null
                        } else {
                            activeCctvRouteAnalysis?.mergeWith(analysis) ?: analysis
                        }
                    },
                    onRouteSearchFailed = { reason ->
                        isRouteRecalculationInProgress = false
                        activeCctvRouteAnalysis = null
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
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 12.dp, end = 92.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = guidanceDirectionSymbol(guidanceStep),
                                style = MaterialTheme.typography.displaySmall
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = guidanceStep?.maneuver?.ifBlank { "다음 안내" }
                                        ?: "경로 안내 준비 중",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                distanceToGuidanceStep?.let { distanceMeters ->
                                    Text(text = "${distanceMeters}m 앞")
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        SosFloatingActionButton(onSosActivated = onSosActivated)
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Button(onClick = { tMapView?.mapZoomIn() }) {
                                Text("+")
                            }
                            Button(onClick = { tMapView?.mapZoomOut() }) {
                                Text("-")
                            }
                            Button(
                                enabled = uiState.currentLatitude != null &&
                                    uiState.currentLongitude != null,
                                onClick = recenterToCurrentLocation
                            ) {
                                Text("내 위치")
                            }
                        }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = uiState.routeSummary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                LinearProgressIndicator(
                                    progress = { uiState.routeProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "진행률 ${(uiState.routeProgress * 100).toInt()}%")
                                    OutlinedButton(
                                        onClick = { isNavigationEndConfirmationVisible = true }
                                    ) {
                                        Text("안내 종료")
                                    }
                                }
                            }
                        }
                    }
                }
                if (!isNavigationMode) {
                    Button(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        enabled = uiState.currentLatitude != null && uiState.currentLongitude != null,
                        onClick = recenterToCurrentLocation
                    ) {
                        Text("내 위치")
                    }
                }
                if (!isNavigationMode) {
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
                }
            }
            val selectedRouteOptionPreview = routeDistancePreview.optionFor(selectedRouteMode)
            val isSelectedRoutePreviewLoading =
                selectedRouteOptionPreview?.isLoading == true
            if (!isNavigationMode) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = uiState.mapStatusLabel)
                        Text(text = uiState.currentLocationLabel)
                        Text(text = uiState.gpsSignalLabel)
                        Text(text = if (isFollowingCurrentLocation) "자동 추적: 켜짐" else "자동 추적: 꺼짐")
                        Text(text = uiState.destinationLabel)
                        uiState.routeSearchMessage?.let { Text(text = it) }
                    }
                }
            }
            if (!isNavigationMode) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "경로 유형",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RouteModeButton(
                                modifier = Modifier.weight(1f),
                                title = "일반 경로",
                                supportingText = routeOptionPreviewLabel(routeDistancePreview?.general),
                                isSelected = selectedRouteMode == RouteMode.GENERAL,
                                onClick = {
                                    selectedRouteMode = RouteMode.GENERAL
                                    activeCctvWaypointCount = 0
                                    activeCctvRouteAnalysis = null
                                }
                            )
                            RouteModeButton(
                                modifier = Modifier.weight(1f),
                                title = "CCTV·가로등",
                                supportingText = routeOptionPreviewLabel(routeDistancePreview?.cctvSafe),
                                isSelected = selectedRouteMode == RouteMode.CCTV_SAFE,
                                onClick = { selectedRouteMode = RouteMode.CCTV_SAFE }
                            )
                        }
                        Text(
                            text = routeSelectionDescription(
                                selectedRouteMode = selectedRouteMode,
                                preview = routeDistancePreview
                            )
                        )
                    }
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.currentLatitude != null &&
                        uiState.currentLongitude != null &&
                        uiState.selectedDestination != null &&
                        !uiState.isRouteSearchInProgress &&
                        !isSelectedRoutePreviewLoading,
                    onClick = {
                        activeCctvWaypointCount = 0
                        activeCctvRouteAnalysis = null
                        onRouteSearchStarted()
                        routeSearchRequestId += 1
                    }
                ) {
                    Text(
                        if (uiState.isRouteSearchInProgress) {
                            "경로 준비 중..."
                        } else if (isSelectedRoutePreviewLoading) {
                            "거리 계산 중..."
                        } else {
                            "이 경로로 시작"
                        }
                    )
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

@SuppressLint("ClickableViewAccessibility")
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
    routeMode: RouteMode,
    isFollowingCurrentLocation: Boolean,
    onMapInteraction: () -> Unit,
    onMapTouchStateChanged: (Boolean) -> Unit,
    onMapReady: () -> Unit,
    onApiKeyFailed: (String?) -> Unit,
    onMapViewCreated: (TMapView) -> Unit,
    onRouteSearchCompleted: (Int) -> Unit,
    onRouteSearchProgress: (String) -> Unit,
    onCctvWaypointCountChanged: (Int) -> Unit,
    onCctvRouteAnalysisChanged: (CctvSafeRouteAnalysis?) -> Unit,
    onRouteSearchFailed: (String) -> Unit,
    onRoutePointsChanged: (List<TMapPoint>) -> Unit,
    onRouteGuidanceStepsChanged: (List<RouteGuidanceStep>) -> Unit
) {
    var lastDestinationFocusRequestId by remember { mutableIntStateOf(0) }
    val renderState = remember { MapRenderState() }
    val currentOnMapTouchStateChanged by rememberUpdatedState(onMapTouchStateChanged)

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
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN ->
                            currentOnMapTouchStateChanged(true)
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL ->
                            currentOnMapTouchStateChanged(false)
                    }
                    false
                }
                setSKTMapApiKey(apiKey)
            }
        },
        update = { view ->
            if (latitude != null && longitude != null) {
                val shouldUpdateLocationPoint = shouldUpdateCoordinate(
                    lastLatitude = renderState.locationPointLatitude,
                    lastLongitude = renderState.locationPointLongitude,
                    latitude = latitude,
                    longitude = longitude,
                    minDistanceMeters = CURRENT_LOCATION_MARKER_MIN_MOVE_METERS
                )
                val shouldRecenter = isFollowingCurrentLocation &&
                    (
                        renderState.recenterRequestId != recenterRequestId ||
                            shouldUpdateCoordinate(
                                lastLatitude = renderState.centeredLatitude,
                                lastLongitude = renderState.centeredLongitude,
                                latitude = latitude,
                                longitude = longitude,
                                minDistanceMeters = CURRENT_LOCATION_MARKER_MIN_MOVE_METERS
                            )
                    )
                if (shouldUpdateLocationPoint || shouldRecenter) {
                    Log.d(
                        TMAP_LOG_TAG,
                        "Updating current location: $latitude, $longitude (follow: $isFollowingCurrentLocation, request: $recenterRequestId)"
                    )
                }
                runCatching {
                    if (shouldUpdateLocationPoint) {
                        view.setLocationPoint(latitude, longitude)
                        renderState.locationPointLatitude = latitude
                        renderState.locationPointLongitude = longitude
                    }
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
                val bearingBucketDegrees = quantizeBearingDegrees(bearingDegrees)
                val shouldUpdateCurrentMarker = !renderState.hasCurrentLocationMarker ||
                    renderState.currentMarkerBearingDegrees != bearingBucketDegrees ||
                    shouldUpdateCoordinate(
                        lastLatitude = renderState.currentMarkerLatitude,
                        lastLongitude = renderState.currentMarkerLongitude,
                        latitude = latitude,
                        longitude = longitude,
                        minDistanceMeters = CURRENT_LOCATION_MARKER_MIN_MOVE_METERS
                    )
                runCatching {
                    if (shouldUpdateCurrentMarker) {
                        val currentLocationMarker = createCurrentLocationMarker(
                            latitude = latitude,
                            longitude = longitude,
                            icon = renderState.currentLocationIconFor(bearingBucketDegrees)
                        )
                        if (renderState.hasCurrentLocationMarker) {
                            view.updateTMapMarkerItem(currentLocationMarker)
                        } else {
                            view.addTMapMarkerItem(currentLocationMarker)
                            renderState.hasCurrentLocationMarker = true
                        }
                        renderState.currentMarkerLatitude = latitude
                        renderState.currentMarkerLongitude = longitude
                        renderState.currentMarkerBearingDegrees = bearingBucketDegrees
                    }
                }.onFailure { error ->
                    Log.w(TMAP_LOG_TAG, "Failed to show the current location marker.", error)
                }
                val previousAccuracyMeters = renderState.accuracyMeters
                val shouldUpdateAccuracyCircle = accuracyMeters != null &&
                    (
                        previousAccuracyMeters == null ||
                            abs(previousAccuracyMeters - accuracyMeters) >=
                            CURRENT_LOCATION_ACCURACY_MIN_CHANGE_METERS ||
                            shouldUpdateCoordinate(
                                lastLatitude = renderState.accuracyLatitude,
                                lastLongitude = renderState.accuracyLongitude,
                                latitude = latitude,
                                longitude = longitude,
                                minDistanceMeters = CURRENT_LOCATION_ACCURACY_MIN_MOVE_METERS
                            )
                    )
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
                val startPoint = TMapPoint(latitude, longitude)
                val destinationPoint = TMapPoint(destination.latitude, destination.longitude)
                when (routeMode) {
                    RouteMode.GENERAL -> findAndShowPedestrianRoute(
                        view = view,
                        startPoint = startPoint,
                        destinationPoint = destinationPoint,
                        onRouteSearchCompleted = onRouteSearchCompleted,
                        onRouteSearchFailed = onRouteSearchFailed,
                        onRoutePointsChanged = onRoutePointsChanged,
                        onRouteGuidanceStepsChanged = onRouteGuidanceStepsChanged
                    ).also {
                        clearRouteCctvMarkers(view)
                        clearRouteStreetlightMarkers(view)
                        onCctvWaypointCountChanged(0)
                        onCctvRouteAnalysisChanged(null)
                    }
                    RouteMode.CCTV_SAFE -> findAndShowCctvSafeRoute(
                        view = view,
                        startPoint = startPoint,
                        destinationPoint = destinationPoint,
                        destinationAddress = destination.address,
                        onRouteSearchCompleted = onRouteSearchCompleted,
                        onRouteSearchProgress = onRouteSearchProgress,
                        onCctvWaypointCountChanged = onCctvWaypointCountChanged,
                        onCctvRouteAnalysisChanged = onCctvRouteAnalysisChanged,
                        onRouteSearchFailed = onRouteSearchFailed,
                        onRoutePointsChanged = onRoutePointsChanged,
                        onRouteGuidanceStepsChanged = onRouteGuidanceStepsChanged
                    )
                }
            }
        }
    )
}

@Composable
private fun RouteModeButton(
    modifier: Modifier,
    title: String,
    supportingText: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val content: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title)
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    if (isSelected) {
        Button(
            modifier = modifier,
            onClick = onClick
        ) {
            content()
        }
    } else {
        OutlinedButton(
            modifier = modifier,
            onClick = onClick
        ) {
            content()
        }
    }
}

private fun guidanceDirectionSymbol(guidanceStep: RouteGuidanceStep?): String {
    val guidanceText = listOfNotNull(
        guidanceStep?.maneuver,
        guidanceStep?.instruction
    ).joinToString(" ")
    return when {
        guidanceText.contains("좌회전") -> "←"
        guidanceText.contains("우회전") -> "→"
        guidanceText.contains("직진") -> "↑"
        guidanceText.contains("유턴") -> "↩"
        guidanceText.contains("횡단") || guidanceText.contains("건너") -> "↗"
        guidanceText.contains("목적지") -> "◎"
        else -> "◆"
    }
}

private fun RouteDistancePreview?.optionFor(routeMode: RouteMode): RouteOptionPreview? {
    return when (routeMode) {
        RouteMode.GENERAL -> this?.general
        RouteMode.CCTV_SAFE -> this?.cctvSafe
    }
}

private fun routeOptionPreviewLabel(preview: RouteOptionPreview?): String {
    return when {
        preview == null -> "목적지 선택 후 계산"
        preview.isLoading -> "계산 중..."
        preview.distanceMeters != null -> {
            "${formatRouteDistance(preview.distanceMeters)} · 약 ${walkingMinutes(preview.distanceMeters)}분"
        }
        preview.errorMessage != null -> "계산 실패"
        else -> "계산 대기"
    }
}

private fun routeSelectionDescription(
    selectedRouteMode: RouteMode,
    preview: RouteDistancePreview?
): String {
    if (preview == null) {
        return "목적지를 선택하면 실제 보행 거리로 두 경로를 비교합니다."
    }
    val selectedPreview = preview.optionFor(selectedRouteMode)
    selectedPreview?.statusMessage?.let { return it }
    selectedPreview?.errorMessage?.let { error ->
        return "$error 선택하면 경로 검색을 다시 시도합니다."
    }
    val selectedDistance = selectedPreview?.distanceMeters
    val generalDistance = preview.general.distanceMeters
    return when (selectedRouteMode) {
        RouteMode.GENERAL -> {
            if (selectedDistance != null) {
                "거리 중심의 일반 보행 경로입니다."
            } else {
                "일반 경로 거리를 계산하는 중입니다."
            }
        }
        RouteMode.CCTV_SAFE -> {
            if (selectedDistance == null) {
                return "CCTV와 가로등 공공데이터를 참고해 인접 구간을 계산하는 중입니다."
            }
            val waypointCount = selectedPreview.waypointCount ?: 0
            val baseDescription = if (waypointCount > 0) {
                "CCTV·가로등 인접 구간 ${waypointCount}곳을 반영합니다."
            } else {
                "추가 우회 없이 CCTV·가로등 인접 정보를 표시합니다."
            }
            if (generalDistance == null) {
                baseDescription
            } else {
                "$baseDescription ${formatRouteDistanceDelta(selectedDistance - generalDistance)}"
            }
        }
    }
}

private fun formatRouteDistance(distanceMeters: Int): String {
    return if (distanceMeters >= 1_000) {
        String.format(Locale.KOREAN, "%.1fkm", distanceMeters / 1_000.0)
    } else {
        "${distanceMeters}m"
    }
}

private fun formatRouteDistanceDelta(deltaMeters: Int): String {
    return when {
        deltaMeters > 0 -> "일반 경로보다 ${formatRouteDistance(deltaMeters)} 더 깁니다."
        deltaMeters < 0 -> "일반 경로보다 ${formatRouteDistance(-deltaMeters)} 더 짧습니다."
        else -> "일반 경로와 거리가 같습니다."
    }
}

private fun walkingMinutes(distanceMeters: Int): Int {
    return (distanceMeters / 80f).toInt().coerceAtLeast(1)
}

private fun shouldUpdateCoordinate(
    lastLatitude: Double?,
    lastLongitude: Double?,
    latitude: Double,
    longitude: Double,
    minDistanceMeters: Float
): Boolean {
    if (lastLatitude == null || lastLongitude == null) return true
    return calculateDistanceMeters(
        startLatitude = lastLatitude,
        startLongitude = lastLongitude,
        endLatitude = latitude,
        endLongitude = longitude
    ) >= minDistanceMeters
}

private fun quantizeBearingDegrees(bearingDegrees: Float): Int {
    if (bearingDegrees.isNaN()) return 0
    val bucketDegrees = (normalizeDegrees(bearingDegrees) / CURRENT_LOCATION_BEARING_BUCKET_DEGREES)
        .roundToInt() * CURRENT_LOCATION_BEARING_BUCKET_DEGREES
    return bucketDegrees % 360
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
    icon: Bitmap
): TMapMarkerItem {
    return TMapMarkerItem().apply {
        setId(CURRENT_LOCATION_MARKER_ID)
        setTMapPoint(TMapPoint(latitude, longitude))
        setIcon(icon)
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

private fun showRouteCctvMarkers(
    view: TMapView,
    routePoints: List<TMapPoint>,
    coordinates: List<CctvCoordinate>
): List<CctvCoordinate> {
    clearRouteCctvMarkers(view)
    if (routePoints.isEmpty()) return emptyList()
    val routeCctvCoordinates = coordinates
        .distinctBy(CctvCoordinate::address)
        .filter { coordinate ->
            isCoordinateNearRoute(
                coordinate = coordinate,
                routePoints = routePoints,
                radiusMeters = ROUTE_CCTV_MARKER_RADIUS_METERS
            )
        }
        .sortedBy { coordinate ->
            routePoints.indexOfNearestRoutePoint(coordinate)
        }
    routeCctvCoordinates.forEachIndexed { index, coordinate ->
        view.addTMapMarkerItem(
            TMapMarkerItem().apply {
                setId("$ROUTE_CCTV_MARKER_ID_PREFIX$index")
                setTMapPoint(TMapPoint(coordinate.latitude, coordinate.longitude))
                setIcon(createRouteCctvIcon())
                setPosition(0.5f, 0.5f)
                setCalloutTitle("경로 주변 CCTV ${index + 1}")
                setCalloutSubTitle("카메라 ${coordinate.cameraCount}대")
                setCanShowCallout(true)
                setVisible(true)
            }
        )
    }
    return routeCctvCoordinates
}

private fun showRouteStreetlightMarkers(
    view: TMapView,
    routePoints: List<TMapPoint>,
    coordinates: List<StreetlightCoordinate>
): List<StreetlightCoordinate> {
    clearRouteStreetlightMarkers(view)
    if (routePoints.isEmpty()) return emptyList()
    val routeStreetlightCoordinates = coordinates
        .distinctBy { coordinate ->
            Triple(coordinate.latitude, coordinate.longitude, coordinate.address)
        }
        .filter { coordinate ->
            isStreetlightNearRoute(
                coordinate = coordinate,
                routePoints = routePoints,
                radiusMeters = ROUTE_STREETLIGHT_MARKER_RADIUS_METERS
            )
        }
        .sortedBy { coordinate ->
            routePoints.indexOfNearestRoutePoint(coordinate)
        }
    routeStreetlightCoordinates.forEachIndexed { index, coordinate ->
        val fixtureText = coordinate.fixtureType.takeIf(String::isNotBlank)
            ?.let { " · $it" }
            .orEmpty()
        view.addTMapMarkerItem(
            TMapMarkerItem().apply {
                setId("$ROUTE_STREETLIGHT_MARKER_ID_PREFIX$index")
                setTMapPoint(TMapPoint(coordinate.latitude, coordinate.longitude))
                setIcon(createRouteStreetlightIcon())
                setPosition(0.5f, 0.5f)
                setCalloutTitle("경로 주변 가로등 ${index + 1}")
                setCalloutSubTitle("총 ${coordinate.lightCount}등$fixtureText")
                setCanShowCallout(true)
                setVisible(true)
            }
        )
    }
    return routeStreetlightCoordinates
}

private fun refreshRouteCctvMarkers(
    view: TMapView,
    routePoints: List<TMapPoint>,
    addressHints: List<String>,
    analysis: CctvSafeRouteAnalysis,
    onCctvWaypointCountChanged: (Int) -> Unit,
    onCctvRouteAnalysisChanged: (CctvSafeRouteAnalysis?) -> Unit
) {
    CctvRepository(view.context).loadCoordinates(
        addressHints = addressHints,
        hasEnoughCoordinates = { false },
        onProgress = {},
        onCompleted = { refreshedCoordinates ->
            val routeCctvCoordinates = showRouteCctvMarkers(
                view = view,
                routePoints = routePoints,
                coordinates = refreshedCoordinates
            )
            onCctvWaypointCountChanged(routeCctvCoordinates.size)
            onCctvRouteAnalysisChanged(
                analysis.copy(routeCctvCount = routeCctvCoordinates.size)
            )
        },
        onFailed = {
            // Initial route markers are already shown; this background refresh is best-effort.
        },
        stopWhenEnoughCoordinates = false,
        maxGeocodingAttempts = Int.MAX_VALUE
    )
}

private fun clearRouteCctvMarkers(view: TMapView) {
    repeat(ROUTE_CCTV_MARKER_CLEAR_LIMIT) { index ->
        view.removeTMapMarkerItem("$ROUTE_CCTV_MARKER_ID_PREFIX$index")
    }
}

private fun clearRouteStreetlightMarkers(view: TMapView) {
    repeat(ROUTE_STREETLIGHT_MARKER_CLEAR_LIMIT) { index ->
        view.removeTMapMarkerItem("$ROUTE_STREETLIGHT_MARKER_ID_PREFIX$index")
    }
}

private fun createRouteCctvIcon(): Bitmap {
    val size = 48
    val center = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    val cctvPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(46, 125, 50)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, 20f, outlinePaint)
    canvas.drawCircle(center, center, 16f, cctvPaint)
    canvas.drawRect(13f, 19f, 35f, 29f, outlinePaint)
    canvas.drawCircle(30f, center, 4f, cctvPaint)
    return bitmap
}

private fun createRouteStreetlightIcon(): Bitmap {
    val size = 48
    val center = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    val lampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 193, 7)
        style = Paint.Style.FILL
    }
    val polePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(245, 124, 0)
        style = Paint.Style.FILL
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawCircle(center, center, 20f, outlinePaint)
    canvas.drawCircle(center, 15f, 9f, lampPaint)
    canvas.drawLine(center, 23f, center, 37f, polePaint)
    canvas.drawLine(center, 24f, 15f, 30f, polePaint)
    canvas.drawLine(17f, 38f, 31f, 38f, polePaint)
    return bitmap
}

private fun isCoordinateNearRoute(
    coordinate: CctvCoordinate,
    routePoints: List<TMapPoint>,
    radiusMeters: Float
): Boolean {
    return distanceFromCoordinateToRouteMeters(
        latitude = coordinate.latitude,
        longitude = coordinate.longitude,
        routePoints = routePoints
    ) <= radiusMeters
}

private fun isStreetlightNearRoute(
    coordinate: StreetlightCoordinate,
    routePoints: List<TMapPoint>,
    radiusMeters: Float
): Boolean {
    return distanceFromCoordinateToRouteMeters(
        latitude = coordinate.latitude,
        longitude = coordinate.longitude,
        routePoints = routePoints
    ) <= radiusMeters
}

private fun List<TMapPoint>.indexOfNearestRoutePoint(coordinate: CctvCoordinate): Int {
    return indexOfNearestRoutePoint(
        latitude = coordinate.latitude,
        longitude = coordinate.longitude
    )
}

private fun List<TMapPoint>.indexOfNearestRoutePoint(coordinate: StreetlightCoordinate): Int {
    return indexOfNearestRoutePoint(
        latitude = coordinate.latitude,
        longitude = coordinate.longitude
    )
}

private fun List<TMapPoint>.indexOfNearestRoutePoint(latitude: Double, longitude: Double): Int {
    return indices.minByOrNull { index ->
        val routePoint = this[index]
        calculateDistanceMeters(
            startLatitude = latitude,
            startLongitude = longitude,
            endLatitude = routePoint.latitude,
            endLongitude = routePoint.longitude
        )
    } ?: Int.MAX_VALUE
}

private fun distanceFromCoordinateToRouteMeters(
    latitude: Double,
    longitude: Double,
    routePoints: List<TMapPoint>
): Double {
    if (routePoints.isEmpty()) return Double.MAX_VALUE
    if (routePoints.size == 1) {
        val routePoint = routePoints.first()
        return calculateDistanceMeters(
            startLatitude = latitude,
            startLongitude = longitude,
            endLatitude = routePoint.latitude,
            endLongitude = routePoint.longitude
        ).toDouble()
    }
    val averageLatitude = (
        latitude +
            routePoints.first().latitude +
            routePoints.last().latitude
        ) / 3.0
    val coordinatePoint = projectToFlatPoint(
        latitude = latitude,
        longitude = longitude,
        originLatitude = latitude,
        originLongitude = longitude,
        averageLatitude = averageLatitude
    )
    return routePoints
        .zipWithNext()
        .minOf { (startPoint, endPoint) ->
            distanceFromPointToSegment(
                point = coordinatePoint,
                segmentStart = projectToFlatPoint(
                    latitude = startPoint.latitude,
                    longitude = startPoint.longitude,
                    originLatitude = latitude,
                    originLongitude = longitude,
                    averageLatitude = averageLatitude
                ),
                segmentEnd = projectToFlatPoint(
                    latitude = endPoint.latitude,
                    longitude = endPoint.longitude,
                    originLatitude = latitude,
                    originLongitude = longitude,
                    averageLatitude = averageLatitude
                )
            )
        }
}

private fun projectToFlatPoint(
    latitude: Double,
    longitude: Double,
    originLatitude: Double,
    originLongitude: Double,
    averageLatitude: Double
): FlatPoint {
    val longitudeMetersPerDegree = METERS_PER_LATITUDE_DEGREE * cos(Math.toRadians(averageLatitude))
    return FlatPoint(
        x = (longitude - originLongitude) * longitudeMetersPerDegree,
        y = (latitude - originLatitude) * METERS_PER_LATITUDE_DEGREE
    )
}

private fun distanceFromPointToSegment(
    point: FlatPoint,
    segmentStart: FlatPoint,
    segmentEnd: FlatPoint
): Double {
    val segmentX = segmentEnd.x - segmentStart.x
    val segmentY = segmentEnd.y - segmentStart.y
    val segmentLengthSquared = segmentX * segmentX + segmentY * segmentY
    if (segmentLengthSquared <= 0.0) {
        return hypot(point.x - segmentStart.x, point.y - segmentStart.y)
    }
    val projection = (
        (point.x - segmentStart.x) * segmentX +
            (point.y - segmentStart.y) * segmentY
        ) / segmentLengthSquared
    val clampedProjection = projection.coerceIn(0.0, 1.0)
    val closestPoint = FlatPoint(
        x = segmentStart.x + segmentX * clampedProjection,
        y = segmentStart.y + segmentY * clampedProjection
    )
    return hypot(point.x - closestPoint.x, point.y - closestPoint.y)
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
    onRouteGuidanceStepsChanged: (List<RouteGuidanceStep>) -> Unit,
    onRouteShown: ((List<TMapPoint>, Int) -> Unit)? = null
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
                        showPedestrianRoute(
                            view = view,
                            polyLine = polyLine,
                            onRouteSearchCompleted = onRouteSearchCompleted,
                            onRouteSearchFailed = onRouteSearchFailed,
                            onRoutePointsChanged = onRoutePointsChanged,
                            onRouteGuidanceStepsChanged = onRouteGuidanceStepsChanged,
                            onRouteShown = onRouteShown
                        )
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

private fun findPedestrianRouteDistance(
    view: TMapView,
    startPoint: TMapPoint,
    destinationPoint: TMapPoint,
    onCompleted: (Int?) -> Unit
) {
    runCatching {
        TMapData().findPathDataWithType(
            TMapData.TMapPathType.PEDESTRIAN_PATH,
            startPoint,
            destinationPoint,
            object : TMapData.OnFindPathDataWithTypeListener {
                override fun onFindPathDataWithType(polyLine: com.skt.tmap.overlay.TMapPolyLine?) {
                    view.post {
                        onCompleted(polyLine?.takeIf { it.linePointList.isNotEmpty() }
                            ?.let(::calculatePolylineDistanceMeters))
                    }
                }
            }
        )
    }.onFailure { error ->
        Log.w(TMAP_LOG_TAG, "Failed to calculate baseline pedestrian route distance.", error)
        view.post { onCompleted(null) }
    }
}

private fun findCctvSafeRouteDistancePreview(
    view: TMapView,
    startPoint: TMapPoint,
    destinationPoint: TMapPoint,
    destinationAddress: String,
    generalDistanceMeters: Int?,
    onProgress: (String) -> Unit,
    onCompleted: (CctvSafeRouteDistancePreview) -> Unit,
    onFailed: (String) -> Unit
) {
    onProgress("현재 위치 주변 CCTV를 찾는 중입니다.")
    findAddressForPoint(startPoint) { startAddress ->
        CctvRepository(view.context).loadCoordinates(
            addressHints = listOf(startAddress, destinationAddress).filter(String::isNotBlank),
            hasEnoughCoordinates = { coordinates ->
                CctvSafeRoutePlanner.countRelevantCoordinates(
                    startLatitude = startPoint.latitude,
                    startLongitude = startPoint.longitude,
                    destinationLatitude = destinationPoint.latitude,
                    destinationLongitude = destinationPoint.longitude,
                    cctvCoordinates = coordinates
                ) >= MIN_RELEVANT_CCTV_COORDINATE_COUNT_FOR_A_STAR
            },
            onProgress = onProgress,
            onCompleted = { coordinates ->
                onProgress("가로등 정보를 경로 점수에 반영하는 중입니다.")
                val completeWithStreetlights = { streetlightCoordinates: List<StreetlightCoordinate> ->
                    val plan = CctvSafeRoutePlanner.plan(
                        startLatitude = startPoint.latitude,
                        startLongitude = startPoint.longitude,
                        destinationLatitude = destinationPoint.latitude,
                        destinationLongitude = destinationPoint.longitude,
                        cctvCoordinates = coordinates,
                        streetlightCoordinates = streetlightCoordinates,
                        maxWaypointCount = MAX_CCTV_WAYPOINT_COUNT
                    )
                    if (plan.waypoints.isEmpty()) {
                        if (generalDistanceMeters != null) {
                            onCompleted(
                                CctvSafeRouteDistancePreview(
                                    distanceMeters = generalDistanceMeters,
                                    waypointCount = 0,
                                    candidateCctvCount = plan.candidateCctvCount
                                )
                            )
                        } else {
                            findPedestrianRouteDistance(
                                view = view,
                                startPoint = startPoint,
                                destinationPoint = destinationPoint
                            ) { fallbackDistanceMeters ->
                                onCompleted(
                                    CctvSafeRouteDistancePreview(
                                        distanceMeters = fallbackDistanceMeters,
                                        waypointCount = 0,
                                        candidateCctvCount = plan.candidateCctvCount
                                    )
                                )
                            }
                        }
                    } else {
                        onProgress("CCTV가 가까운 구간의 실제 보행 거리를 계산하는 중입니다.")
                        findSegmentedPedestrianRouteDistance(
                            view = view,
                            routeStops = listOf(startPoint) +
                                plan.waypoints.map { coordinate ->
                                    TMapPoint(coordinate.latitude, coordinate.longitude)
                                } +
                                destinationPoint
                        ) { distanceMeters ->
                            onCompleted(
                                CctvSafeRouteDistancePreview(
                                    distanceMeters = distanceMeters,
                                    waypointCount = plan.waypoints.size,
                                    candidateCctvCount = plan.candidateCctvCount
                                )
                            )
                        }
                    }
                }
                StreetlightRepository(view.context).loadCoordinates(
                    onCompleted = completeWithStreetlights,
                    onFailed = { error ->
                        Log.w(TMAP_LOG_TAG, "Failed to load streetlights for preview: $error")
                        completeWithStreetlights(emptyList())
                    }
                )
            },
            onFailed = onFailed
        )
    }
}

private fun findSegmentedPedestrianRouteDistance(
    view: TMapView,
    routeStops: List<TMapPoint>,
    onCompleted: (Int?) -> Unit
) {
    if (routeStops.size < 2) {
        onCompleted(null)
        return
    }

    var segmentIndex = 0
    var totalDistanceMeters = 0

    fun requestNextSegment() {
        if (segmentIndex >= routeStops.lastIndex) {
            onCompleted(totalDistanceMeters)
            return
        }
        val segmentStart = routeStops[segmentIndex]
        val segmentEnd = routeStops[segmentIndex + 1]
        runCatching {
            TMapData().findPathDataWithType(
                TMapData.TMapPathType.PEDESTRIAN_PATH,
                segmentStart,
                segmentEnd,
                object : TMapData.OnFindPathDataWithTypeListener {
                    override fun onFindPathDataWithType(
                        polyLine: com.skt.tmap.overlay.TMapPolyLine?
                    ) {
                        view.post {
                            if (polyLine == null || polyLine.linePointList.isEmpty()) {
                                onCompleted(null)
                                return@post
                            }
                            totalDistanceMeters += calculatePolylineDistanceMeters(polyLine)
                            segmentIndex += 1
                            requestNextSegment()
                        }
                    }
                }
            )
        }.onFailure { error ->
            Log.w(TMAP_LOG_TAG, "Failed to calculate segmented route distance.", error)
            view.post { onCompleted(null) }
        }
    }

    requestNextSegment()
}

private fun findAndShowSegmentedPedestrianRoute(
    view: TMapView,
    routeStops: List<TMapPoint>,
    onRouteSearchCompleted: (Int) -> Unit,
    onRouteSearchFailed: (String) -> Unit,
    onRoutePointsChanged: (List<TMapPoint>) -> Unit,
    onRouteGuidanceStepsChanged: (List<RouteGuidanceStep>) -> Unit,
    onRouteShown: ((List<TMapPoint>, Int) -> Unit)? = null
) {
    if (routeStops.size < 2) {
        onRouteSearchFailed("경로를 계산할 지점이 부족합니다")
        return
    }

    val combinedRoutePoints = ArrayList<TMapPoint>()
    var segmentIndex = 0

    fun appendSegment(polyLine: com.skt.tmap.overlay.TMapPolyLine): Boolean {
        val segmentPoints = polyLine.linePointList
        if (segmentPoints.isEmpty()) return false
        if (combinedRoutePoints.isEmpty()) {
            combinedRoutePoints.addAll(segmentPoints)
        } else {
            combinedRoutePoints.addAll(segmentPoints.drop(1))
        }
        return true
    }

    fun showCombinedRoute() {
        val combinedPolyLine = com.skt.tmap.overlay.TMapPolyLine().apply {
            combinedRoutePoints.forEach(::addLinePoint)
        }
        showPedestrianRoute(
            view = view,
            polyLine = combinedPolyLine,
            onRouteSearchCompleted = onRouteSearchCompleted,
            onRouteSearchFailed = onRouteSearchFailed,
            onRoutePointsChanged = onRoutePointsChanged,
            onRouteGuidanceStepsChanged = onRouteGuidanceStepsChanged,
            onRouteShown = onRouteShown
        )
    }

    fun requestNextSegment() {
        if (segmentIndex >= routeStops.lastIndex) {
            view.post(::showCombinedRoute)
            return
        }
        val segmentStart = routeStops[segmentIndex]
        val segmentEnd = routeStops[segmentIndex + 1]
        runCatching {
            TMapData().findPathDataWithType(
                TMapData.TMapPathType.PEDESTRIAN_PATH,
                segmentStart,
                segmentEnd,
                object : TMapData.OnFindPathDataWithTypeListener {
                    override fun onFindPathDataWithType(
                        polyLine: com.skt.tmap.overlay.TMapPolyLine?
                    ) {
                        view.post {
                            if (polyLine == null || !appendSegment(polyLine)) {
                                onRouteSearchFailed("경유 구간 경로 검색 결과가 없습니다")
                                return@post
                            }
                            segmentIndex += 1
                            requestNextSegment()
                        }
                    }
                }
            )
        }.onFailure { error ->
            Log.e(TMAP_LOG_TAG, "Failed to search segmented pedestrian route.", error)
            view.post {
                onRouteSearchFailed(error.message ?: "경유 구간 경로를 검색하지 못했습니다")
            }
        }
    }

    requestNextSegment()
}

private fun findAndShowCctvSafeRoute(
    view: TMapView,
    startPoint: TMapPoint,
    destinationPoint: TMapPoint,
    destinationAddress: String,
    onRouteSearchCompleted: (Int) -> Unit,
    onRouteSearchProgress: (String) -> Unit,
    onCctvWaypointCountChanged: (Int) -> Unit,
    onCctvRouteAnalysisChanged: (CctvSafeRouteAnalysis?) -> Unit,
    onRouteSearchFailed: (String) -> Unit,
    onRoutePointsChanged: (List<TMapPoint>) -> Unit,
    onRouteGuidanceStepsChanged: (List<RouteGuidanceStep>) -> Unit
) {
    onRouteSearchProgress("일반 경로 기준 거리를 확인하는 중입니다.")
    findPedestrianRouteDistance(
        view = view,
        startPoint = startPoint,
        destinationPoint = destinationPoint
    ) { generalDistanceMeters ->
        onRouteSearchProgress("현재 위치 주변 CCTV를 찾는 중입니다.")
        findAddressForPoint(startPoint) { startAddress ->
            findAndShowCctvSafeRoute(
                view = view,
                startPoint = startPoint,
                destinationPoint = destinationPoint,
                destinationAddress = destinationAddress,
                startAddress = startAddress,
                generalDistanceMeters = generalDistanceMeters,
                onRouteSearchCompleted = onRouteSearchCompleted,
                onRouteSearchProgress = onRouteSearchProgress,
                onCctvWaypointCountChanged = onCctvWaypointCountChanged,
                onCctvRouteAnalysisChanged = onCctvRouteAnalysisChanged,
                onRouteSearchFailed = onRouteSearchFailed,
                onRoutePointsChanged = onRoutePointsChanged,
                onRouteGuidanceStepsChanged = onRouteGuidanceStepsChanged
            )
        }
    }
}

private fun findAndShowCctvSafeRoute(
    view: TMapView,
    startPoint: TMapPoint,
    destinationPoint: TMapPoint,
    destinationAddress: String,
    startAddress: String,
    generalDistanceMeters: Int?,
    onRouteSearchCompleted: (Int) -> Unit,
    onRouteSearchProgress: (String) -> Unit,
    onCctvWaypointCountChanged: (Int) -> Unit,
    onCctvRouteAnalysisChanged: (CctvSafeRouteAnalysis?) -> Unit,
    onRouteSearchFailed: (String) -> Unit,
    onRoutePointsChanged: (List<TMapPoint>) -> Unit,
    onRouteGuidanceStepsChanged: (List<RouteGuidanceStep>) -> Unit
) {
    CctvRepository(view.context).loadCoordinates(
        addressHints = listOf(startAddress, destinationAddress).filter(String::isNotBlank),
        hasEnoughCoordinates = { coordinates ->
            CctvSafeRoutePlanner.countRelevantCoordinates(
                startLatitude = startPoint.latitude,
                startLongitude = startPoint.longitude,
                destinationLatitude = destinationPoint.latitude,
                destinationLongitude = destinationPoint.longitude,
                cctvCoordinates = coordinates
            ) >= MIN_RELEVANT_CCTV_COORDINATE_COUNT_FOR_A_STAR
        },
        onProgress = onRouteSearchProgress,
        onCompleted = { coordinates ->
            val showPlanWithStreetlights = showPlan@{ streetlightCoordinates: List<StreetlightCoordinate> ->
            val plan = CctvSafeRoutePlanner.plan(
                startLatitude = startPoint.latitude,
                startLongitude = startPoint.longitude,
                destinationLatitude = destinationPoint.latitude,
                destinationLongitude = destinationPoint.longitude,
                cctvCoordinates = coordinates,
                streetlightCoordinates = streetlightCoordinates,
                maxWaypointCount = MAX_CCTV_WAYPOINT_COUNT
            )
            val waypoints = plan.waypoints
            if (waypoints.isEmpty()) {
                clearRouteCctvMarkers(view)
                clearRouteStreetlightMarkers(view)
                onCctvWaypointCountChanged(0)
                onRouteSearchProgress(
                    if (plan.candidateCctvCount == 0) {
                        "불필요한 단독 경유 후보를 제외하고 일반 보행 경로를 안내합니다."
                    } else {
                        "A* 분석 결과 일반 경로에 추가 우회가 필요하지 않습니다."
                    }
                )
                val analysis = CctvSafeRouteAnalysis(
                    generalDistanceMeters = generalDistanceMeters,
                    safeDistanceMeters = null,
                    candidateCctvCount = plan.candidateCctvCount,
                    selectedWaypointCount = 0,
                    routeCctvCount = null,
                    routeStreetlightLampCount = null,
                    routeStreetlightLocationCount = null,
                    estimatedCoverageRatio = plan.estimatedCoverageRatio,
                    estimatedStreetlightCoverageRatio = plan.estimatedStreetlightCoverageRatio
                )
                onCctvRouteAnalysisChanged(analysis)
                findAndShowPedestrianRoute(
                    view = view,
                    startPoint = startPoint,
                    destinationPoint = destinationPoint,
                    onRouteSearchCompleted = { distanceMeters ->
                        onRouteSearchCompleted(distanceMeters)
                    },
                    onRouteSearchFailed = onRouteSearchFailed,
                    onRoutePointsChanged = onRoutePointsChanged,
                    onRouteGuidanceStepsChanged = onRouteGuidanceStepsChanged,
                    onRouteShown = { routePoints, distanceMeters ->
                        val routeCctvCoordinates = showRouteCctvMarkers(
                            view = view,
                            routePoints = routePoints,
                            coordinates = coordinates
                        )
                        val routeStreetlightCoordinates = showRouteStreetlightMarkers(
                            view = view,
                            routePoints = routePoints,
                            coordinates = streetlightCoordinates
                        )
                        onCctvWaypointCountChanged(routeCctvCoordinates.size)
                        val updatedAnalysis = analysis.copy(
                            generalDistanceMeters = generalDistanceMeters ?: distanceMeters,
                            safeDistanceMeters = distanceMeters,
                            routeCctvCount = routeCctvCoordinates.size,
                            routeStreetlightLampCount = routeStreetlightCoordinates.sumOf { it.lightCount },
                            routeStreetlightLocationCount = routeStreetlightCoordinates.size
                        )
                        onCctvRouteAnalysisChanged(updatedAnalysis)
                        refreshRouteCctvMarkers(
                            view = view,
                            routePoints = routePoints,
                            addressHints = listOf(startAddress, destinationAddress)
                                .filter(String::isNotBlank),
                            analysis = updatedAnalysis,
                            onCctvWaypointCountChanged = onCctvWaypointCountChanged,
                            onCctvRouteAnalysisChanged = onCctvRouteAnalysisChanged
                        )
                        onRouteSearchCompleted(distanceMeters)
                    }
                )
                return@showPlan
            }
            clearRouteCctvMarkers(view)
            clearRouteStreetlightMarkers(view)
            onCctvWaypointCountChanged(0)
            val analysis = CctvSafeRouteAnalysis(
                generalDistanceMeters = generalDistanceMeters,
                safeDistanceMeters = null,
                candidateCctvCount = plan.candidateCctvCount,
                selectedWaypointCount = waypoints.size,
                routeCctvCount = null,
                routeStreetlightLampCount = null,
                routeStreetlightLocationCount = null,
                estimatedCoverageRatio = plan.estimatedCoverageRatio,
                estimatedStreetlightCoverageRatio = plan.estimatedStreetlightCoverageRatio
            )
            onCctvRouteAnalysisChanged(analysis)
            onRouteSearchProgress("CCTV가 가까운 구간을 연결해 우회 경로를 계산하는 중입니다.")
            Log.i(
                TMAP_LOG_TAG,
                "Requesting segmented A* CCTV safe route with ${waypoints.size} waypoints. " +
                    "estimatedDistance=${plan.estimatedDistanceMeters}, " +
                    "coverage=${plan.estimatedCoverageRatio}, " +
                    "candidates=${plan.candidateCctvCount}"
            )
            findAndShowSegmentedPedestrianRoute(
                view = view,
                routeStops = listOf(startPoint) +
                    waypoints.map { coordinate ->
                        TMapPoint(coordinate.latitude, coordinate.longitude)
                    } +
                    destinationPoint,
                onRouteSearchCompleted = { distanceMeters ->
                    onRouteSearchCompleted(distanceMeters)
                },
                onRouteSearchFailed = onRouteSearchFailed,
                onRoutePointsChanged = onRoutePointsChanged,
                onRouteGuidanceStepsChanged = onRouteGuidanceStepsChanged,
                onRouteShown = { routePoints, distanceMeters ->
                    val routeCctvCoordinates = showRouteCctvMarkers(
                        view = view,
                        routePoints = routePoints,
                        coordinates = coordinates
                    )
                    val routeStreetlightCoordinates = showRouteStreetlightMarkers(
                        view = view,
                        routePoints = routePoints,
                        coordinates = streetlightCoordinates
                    )
                    onCctvWaypointCountChanged(routeCctvCoordinates.size)
                    val updatedAnalysis = analysis.copy(
                        safeDistanceMeters = distanceMeters,
                        routeCctvCount = routeCctvCoordinates.size,
                        routeStreetlightLampCount =
                            routeStreetlightCoordinates.sumOf { it.lightCount },
                        routeStreetlightLocationCount =
                            routeStreetlightCoordinates.size
                    )
                    onCctvRouteAnalysisChanged(updatedAnalysis)
                    refreshRouteCctvMarkers(
                        view = view,
                        routePoints = routePoints,
                        addressHints = listOf(startAddress, destinationAddress)
                            .filter(String::isNotBlank),
                        analysis = updatedAnalysis,
                        onCctvWaypointCountChanged = onCctvWaypointCountChanged,
                        onCctvRouteAnalysisChanged = onCctvRouteAnalysisChanged
                    )
                    onRouteSearchCompleted(distanceMeters)
                }
            )
            }
            onRouteSearchProgress("가로등 정보를 경로 점수에 반영하는 중입니다.")
            StreetlightRepository(view.context).loadCoordinates(
                onCompleted = showPlanWithStreetlights,
                onFailed = { error ->
                    Log.w(TMAP_LOG_TAG, "Failed to load streetlights for route scoring: $error")
                    showPlanWithStreetlights(emptyList())
                }
            )
        },
        onFailed = onRouteSearchFailed
    )
}

private fun findAddressForPoint(
    point: TMapPoint,
    onCompleted: (String) -> Unit
) {
    val mainHandler = Handler(Looper.getMainLooper())
    var isCompleted = false
    lateinit var timeout: Runnable

    fun complete(address: String?) {
        if (isCompleted) return
        isCompleted = true
        mainHandler.removeCallbacks(timeout)
        onCompleted(address.orEmpty())
    }

    timeout = Runnable { complete(null) }
    mainHandler.postDelayed(timeout, 1_500L)
    runCatching {
        TMapData().convertGpsToAddress(
            point.latitude,
            point.longitude,
            object : TMapData.OnConvertGPSToAddressListener {
                override fun onConverGPSToAddress(address: String?) {
                    mainHandler.post { complete(address) }
                }
            }
        )
    }.onFailure {
        complete(null)
    }
}

private fun showPedestrianRoute(
    view: TMapView,
    polyLine: com.skt.tmap.overlay.TMapPolyLine?,
    onRouteSearchCompleted: (Int) -> Unit,
    onRouteSearchFailed: (String) -> Unit,
    onRoutePointsChanged: (List<TMapPoint>) -> Unit,
    onRouteGuidanceStepsChanged: (List<RouteGuidanceStep>) -> Unit,
    onRouteShown: ((List<TMapPoint>, Int) -> Unit)? = null
) {
    if (polyLine == null || polyLine.linePointList.isEmpty()) {
        onRouteSearchFailed("경로 검색 결과가 없습니다")
        return
    }
    runCatching {
        polyLine.setID(PEDESTRIAN_ROUTE_ID)
        polyLine.setLineColor(Color.rgb(33, 150, 243))
        polyLine.setLineWidth(8f)
        polyLine.setLineAlpha(220)
        polyLine.passPointList?.clear()
        view.removeTMapPath()
        view.setTMapPath(polyLine)
        view.fitBounds(view.getBoundsFromPoints(polyLine.linePointList))
        val routePoints = polyLine.linePointList.toList()
        onRoutePointsChanged(routePoints)
        val fallbackGuidanceSteps = deriveRouteGuidanceSteps(polyLine.linePointList)
        Log.i(
            TMAP_LOG_TAG,
            "Generated ${fallbackGuidanceSteps.size} guidance steps from the pedestrian route polyline."
        )
        onRouteGuidanceStepsChanged(fallbackGuidanceSteps)
        val distanceMeters = calculatePolylineDistanceMeters(polyLine)
        if (onRouteShown != null) {
            onRouteShown(routePoints, distanceMeters)
        } else {
            onRouteSearchCompleted(distanceMeters)
        }
    }.onFailure { error ->
        Log.e(TMAP_LOG_TAG, "Failed to show pedestrian route.", error)
        onRouteSearchFailed(error.message ?: "unknown error")
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
    val searchQueries = destinationSearchQueries(query)
    val collectedResults = mutableListOf<DestinationSearchResult>()

    fun completeSearch() {
        val results = prioritizeGumiSearchResults(collectedResults)
        ContextCompat.getMainExecutor(context).execute {
            onSearchCompleted(results)
        }
    }

    fun searchNextQuery(index: Int) {
        if (index >= searchQueries.size) {
            completeSearch()
            return
        }

        runCatching {
            TMapData().findAllPOI(
                searchQueries[index],
                10,
                object : TMapData.OnFindAllPOIListener {
                    override fun onFindAllPOI(
                        poiItems: ArrayList<com.skt.tmap.poi.TMapPOIItem>?
                    ) {
                        val results = poiItems.orEmpty().mapNotNull { poiItem ->
                            val point = poiItem.poiPoint ?: return@mapNotNull null
                            DestinationSearchResult(
                                name = poiItem.poiName.orEmpty(),
                                address = poiItem.poiAddress.orEmpty(),
                                latitude = point.latitude,
                                longitude = point.longitude
                            )
                        }
                        collectedResults += results
                        searchNextQuery(index + 1)
                    }
                }
            )
        }.onFailure { error ->
            Log.e(TMAP_LOG_TAG, "Failed to search destination POI.", error)
            if (collectedResults.isNotEmpty() || index < searchQueries.lastIndex) {
                searchNextQuery(index + 1)
            } else {
                ContextCompat.getMainExecutor(context).execute {
                    onSearchFailed(error.message ?: "unknown error")
                }
            }
        }
    }

    searchNextQuery(0)
}

private fun destinationSearchQueries(query: String): List<String> {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isBlank()) return emptyList()
    if (trimmedQuery.contains("구미", ignoreCase = true)) return listOf(trimmedQuery)
    return listOf(
        "구미 $trimmedQuery",
        "구미시 $trimmedQuery",
        trimmedQuery
    ).distinct()
}

private fun prioritizeGumiSearchResults(
    results: List<DestinationSearchResult>
): List<DestinationSearchResult> {
    return results
        .distinctBy { result ->
            listOf(
                result.name.trim(),
                result.address.trim(),
                "%.6f".format(Locale.US, result.latitude),
                "%.6f".format(Locale.US, result.longitude)
            ).joinToString("|")
        }
        .withIndex()
        .sortedWith(
            compareBy<IndexedValue<DestinationSearchResult>> {
                if (it.value.isGumiSearchResult()) 0 else 1
            }.thenBy {
                if (it.value.isGumiSearchResult()) {
                    it.value.distanceFromGumiCenterMeters()
                } else {
                    Float.MAX_VALUE
                }
            }.thenBy {
                it.index
            }
        )
        .map(IndexedValue<DestinationSearchResult>::value)
}

private fun DestinationSearchResult.isGumiSearchResult(): Boolean {
    return name.contains("구미", ignoreCase = true) ||
        address.contains("구미", ignoreCase = true) ||
        distanceFromGumiCenterMeters() <= GUMI_PRIORITY_RADIUS_METERS
}

private fun DestinationSearchResult.distanceFromGumiCenterMeters(): Float {
    val distanceMeters = FloatArray(1)
    Location.distanceBetween(
        GUMI_CENTER_LATITUDE,
        GUMI_CENTER_LONGITUDE,
        latitude,
        longitude,
        distanceMeters
    )
    return distanceMeters[0]
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
