package com.bycho.safereturnhome.ui.screen.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bycho.safereturnhome.BuildConfig
import com.bycho.safereturnhome.ui.state.MapUiState
import com.bycho.safereturnhome.ui.viewmodel.MapViewModel
import com.skt.tmap.TMapView

private const val TMAP_LOG_TAG = "TMapViewContainer"
private const val DEFAULT_LATITUDE = 37.5665
private const val DEFAULT_LONGITUDE = 126.9780

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
        onCurrentLocationUnavailable = viewModel::onCurrentLocationUnavailable
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
    onCurrentLocationLoaded: (Double, Double) -> Unit,
    onCurrentLocationUnavailable: (String) -> Unit
) {
    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onLocationPermissionResult(isGranted)
        if (isGranted) {
            fetchCurrentLocation(
                context = context,
                onLocationLoaded = onCurrentLocationLoaded,
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
                onLocationLoaded = onCurrentLocationLoaded,
                onLocationUnavailable = onCurrentLocationUnavailable
            )
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("경로 설정") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "지도를 불러온 뒤 현재 위치를 확인하고, 다음 단계에서 목적지와 경로를 연결합니다.",
                style = MaterialTheme.typography.bodyLarge
            )
            TMapViewContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(bottom = 4.dp),
                apiKey = if (uiState.hasApiKey) BuildConfig.TMAP_API_KEY else "",
                hasApiKey = uiState.hasApiKey,
                latitude = uiState.currentLatitude,
                longitude = uiState.currentLongitude,
                onMapReady = onMapReady,
                onApiKeyFailed = onApiKeyFailed
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = uiState.mapStatusLabel)
                    Text(text = uiState.currentLocationLabel)
                    Text(text = uiState.destinationLabel)
                    Text(text = uiState.routeSummary)
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onConfirmRouteClick
            ) {
                Text("이 경로로 시작")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onBackClick
            ) {
                Text("뒤로 가기")
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
    onMapReady: () -> Unit,
    onApiKeyFailed: (String?) -> Unit
) {
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

    var isMapViewReady by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TMapView(context).apply {
                isMapViewReady = false
                setSKTMapApiKey(apiKey)
                setOnApiKeyListenerCallback(object : TMapView.OnApiKeyListenerCallback {
                    override fun onSKTMapApikeySucceed() {
                        Log.d(TMAP_LOG_TAG, "TMap API key authentication succeeded.")
                    }

                    override fun onSKTMapApikeyFailed(message: String?) {
                        Log.e(TMAP_LOG_TAG, "TMap API key authentication failed: ${message ?: "unknown error"}")
                        post { onApiKeyFailed(message) }
                    }
                })
                setOnMapReadyListener(object : TMapView.OnMapReadyListener {
                    override fun onMapReady() {
                        post {
                            Log.d(TMAP_LOG_TAG, "TMap view is ready.")
                            isMapViewReady = true
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
            }
        },
        update = { view ->
            if (isMapViewReady && latitude != null && longitude != null) {
                Log.d(TMAP_LOG_TAG, "Moving map to current location: $latitude, $longitude")
                runCatching {
                    view.setLocationPoint(latitude, longitude)
                    view.setIconVisibility(true)
                    view.setZoomLevel(17)
                    view.setCenterPoint(latitude, longitude)
                }.onFailure { error ->
                    Log.e(TMAP_LOG_TAG, "Failed to move map to current location.", error)
                }
            }
        }
    )
}

private fun fetchCurrentLocation(
    context: Context,
    onLocationLoaded: (Double, Double) -> Unit,
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
            onLocationLoaded(location.latitude, location.longitude)
        } else {
            fallbackToLastKnownLocation(locationManager, onLocationLoaded, onLocationUnavailable)
        }
    }
}

private fun fallbackToLastKnownLocation(
    locationManager: LocationManager,
    onLocationLoaded: (Double, Double) -> Unit,
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
        onLocationLoaded(lastKnownLocation.latitude, lastKnownLocation.longitude)
    } else {
        onLocationUnavailable("아직 위치를 확인하지 못했습니다")
    }
}
