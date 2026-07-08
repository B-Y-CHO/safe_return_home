# Legacy Raspberry Pi transport

`RaspberryPiHttpClient.kt`와 `RaspberryWebSocketManager.kt`는 기존 창의설계 버전 호환을 위해 상위 `network` 패키지에 그대로 보존되어 있습니다. 두 클래스는 deprecated 처리되었으며 대회용 런타임에서는 참조하지 않습니다.

대회용 코드는 `DangerEventSource`, `EventHttpClient`, `EventWebSocketClient`, `FastApiDangerEventSource`를 사용합니다. 기존 버전으로 복귀할 때만 Raspberry 클래스를 다시 주입합니다.
