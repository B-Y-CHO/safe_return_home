# safe_return_home

안심 귀가를 돕기 위한 Android 앱 프로젝트입니다. Jetpack Compose 기반으로 화면을 구성했고, TMap SDK를 사용해 지도와 현재 위치를 표시합니다.

## 현재 포함된 내용

- 홈 화면
- 지도 화면
- 귀가/이동 관련 기본 화면 흐름
- 위치 권한 요청
- 현재 위치 조회
- TMap 지도 표시

## 개발 환경

- Android Studio
- Kotlin
- Gradle
- Jetpack Compose

## 실행 전 준비

### 1. Android SDK 경로 설정

로컬 환경의 `local.properties`에 Android SDK 경로를 설정합니다.

```properties
sdk.dir=C:\\Users\\<YOUR_NAME>\\AppData\\Local\\Android\\Sdk
```

### 2. TMap API 키 설정

같은 `local.properties` 파일에 TMap API 키를 넣어야 합니다.

```properties
TMAP_API_KEY=your_tmap_api_key
```

`local.properties`는 Git에 포함되지 않도록 제외되어 있습니다.

## 실행 방법

1. Android Studio에서 프로젝트를 엽니다.
2. `local.properties`에 `sdk.dir`와 `TMAP_API_KEY`를 설정합니다.
3. Gradle Sync를 수행합니다.
4. 에뮬레이터 또는 실제 기기에서 실행합니다.

## 참고 사항

- `app/libs/` 아래의 TMap 관련 AAR 파일을 사용합니다.
- 위치 권한이 허용되어야 현재 위치를 지도에 반영할 수 있습니다.
- 지도/위치 기능은 계속 보완 중입니다.
