package com.bycho.safereturnhome.ui.screen.map

import android.graphics.Bitmap
import com.bycho.safereturnhome.data.FacilityMapPoint
import com.bycho.safereturnhome.data.selectVisibleMapFacilities
import com.skt.tmap.TMapPoint
import com.skt.tmap.TMapView
import com.skt.tmap.overlay.TMapMarkerItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A map owns its layers; only visible markers are changed, in small frame-sized batches. */
internal class FacilityMarkerLayer<T>(
    private val view: TMapView,
    private val prefix: String,
    private val pointOf: (T) -> FacilityMapPoint,
    private val createIcon: () -> Bitmap,
    private val title: String,
    private val subtitle: (T) -> String
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var refreshJob: Job? = null
    private var coordinates: List<T> = emptyList()
    private val displayed = mutableMapOf<FacilityMapPoint, String>()
    private var nextId = 0L

    fun setCoordinates(value: List<T>) {
        coordinates = value
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        if (coordinates.isEmpty()) return
        refreshJob = scope.launch {
            delay(180)
            val top = view.leftTopPoint ?: return@launch
            val bottom = view.rightBottomPoint ?: return@launch
            val width = view.width
            val height = view.height
            val spacing = (48 * view.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            val source = coordinates
            val visible = withContext(Dispatchers.Default) {
                selectVisibleMapFacilities(
                    facilities = source,
                    pointOf = pointOf,
                    south = minOf(top.latitude, bottom.latitude),
                    west = minOf(top.longitude, bottom.longitude),
                    north = maxOf(top.latitude, bottom.latitude),
                    east = maxOf(top.longitude, bottom.longitude),
                    widthPixels = width,
                    heightPixels = height,
                    spacingPixels = spacing
                )
            }
            val wanted = visible.associateBy(pointOf)
            var changes = 0
            for (point in displayed.keys.toList()) {
                if (point in wanted) continue
                view.removeTMapMarkerItem(displayed.remove(point)!!)
                if (++changes % 8 == 0) delay(16)
            }
            for ((point, coordinate) in wanted) {
                if (point in displayed) continue
                val id = "$prefix${nextId++}"
                view.addTMapMarkerItem(TMapMarkerItem().apply {
                    setId(id)
                    setTMapPoint(TMapPoint(point.latitude, point.longitude))
                    setIcon(createIcon())
                    setPosition(0.5f, 0.5f)
                    setCalloutTitle(title)
                    setCalloutSubTitle(subtitle(coordinate))
                    setCanShowCallout(true)
                    setVisible(true)
                })
                displayed[point] = id
                if (++changes % 8 == 0) delay(16)
            }
        }
    }

    fun clear() {
        refreshJob?.cancel()
        coordinates = emptyList()
        displayed.values.forEach(view::removeTMapMarkerItem)
        displayed.clear()
    }

    fun dispose() {
        clear()
        scope.cancel()
    }
}
