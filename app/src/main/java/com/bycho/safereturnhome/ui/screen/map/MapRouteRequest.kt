package com.bycho.safereturnhome.ui.screen.map

internal fun shouldStartMapRouteRequest(
    pending: Boolean,
    mapReady: Boolean,
    requestId: Int,
    handledRequestId: Int,
    hasEndpoints: Boolean
): Boolean = pending && mapReady && hasEndpoints && requestId > handledRequestId

internal fun isMapRouteRequestActive(
    disposed: Boolean,
    pending: Boolean,
    requestId: Int,
    currentRequestId: Int,
    destinationUnchanged: Boolean
): Boolean = !disposed && pending && requestId == currentRequestId && destinationUnchanged
