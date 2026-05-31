# Safe Return Home

안전한 귀가를 돕기 위한 Android 지도 안내 애플리케이션입니다. Jetpack Compose와 TMap Android SDK를 기반으로 현재 위치 추적, 목적지 검색, 보행자 경로 안내를 제공합니다.

## 주요 기능

### 현재 위치 추적

- 위치 권한 요청 및 현재 위치 조회
- GPS와 네트워크 위치 공급자를 이용한 실시간 위치 갱신
- 현재 위치 마커와 GPS 정확도 범위 표시
- GPS 정확도에 따른 신호 상태 표시: `Good`, `Fair`, `Weak`
- 휴대폰 회전에 따라 현재 위치 화살표 방향 갱신

### 지도 조작

- `+`, `-` 버튼을 이용한 지도 확대 및 축소
- `내 위치` 버튼을 이용한 현재 위치 복귀
- 사용자가 지도를 직접 조작하면 자동 추적 해제
- `내 위치` 버튼을 누르면 자동 추적 재활성화

### 목적지 검색

- 장소명 또는 주소 검색
- TMap SDK의 POI 검색 결과 목록 표시
- 검색 결과 선택 시 목적지 마커 표시
- 현재 위치부터 목적지까지 직선 거리와 예상 도보 시간 표시

### 보행자 경로 안내

- TMap 보행자 경로 API를 이용한 실제 경로선 표시
- 안내 시작 후 지도 중심의 전용 안내 화면으로 전환
- 현재 위치 기준 남은 경로 거리와 예상 도보 시간 갱신
- 전체 경로 대비 진행률 바 표시
- 경로 이탈 감지 및 자동 재탐색
- 목적지 도착 감지
- 안내 시작, 재탐색, 도착 시 진동 및 TTS 음성 안내
- 안내 중 화면 자동 꺼짐 방지

### 지도 방향 모드

- 진행 방향 모드: 휴대폰이 향하는 방향이 지도 위쪽을 향하도록 회전
- 북쪽 고정 모드: 지도 위쪽을 항상 북쪽으로 고정
- 안내 화면의 버튼으로 두 모드 전환

## 기술 구성

- Kotlin
- Jetpack Compose
- Material 3
- Android View Interop
- Android `LocationManager`
- Android `TYPE_ROTATION_VECTOR` 센서
- Android `TextToSpeech`
- TMap Android SDK `3.5`
- VSM TMap SDK `2.0.0`

## 요구 사항

- Android Studio
- JDK 17
- Android SDK
- Android 8.0 이상 (`minSdk = 26`)
- TMap API 키
- 위치 권한을 허용할 수 있는 실제 기기 또는 위치가 설정된 에뮬레이터

실제 GPS, 방향 센서, 진동, TTS 동작은 실제 Android 기기에서 확인하는 것을 권장합니다.

## 프로젝트 실행 준비

### 1. 저장소 복제

```bash
git clone https://github.com/B-Y-CHO/safe_return_home.git
cd safe_return_home
```

### 2. `local.properties` 설정

프로젝트 루트의 `local.properties` 파일에 Android SDK 경로와 TMap API 키를 설정합니다.

```properties
sdk.dir=C\:\\Users\\<YOUR_NAME>\\AppData\\Local\\Android\\Sdk
TMAP_API_KEY=your_tmap_api_key
```

Windows의 `.properties` 파일에서는 드라이브 구분자와 경로 구분자를 이스케이프해야 합니다.

`local.properties`는 Git에 포함하지 마세요. API 키를 README, 소스 코드, 이슈, 커밋에 직접 작성하면 안 됩니다.

### 3. TMap API 권한 확인

TMap 콘솔에서 현재 키에 필요한 기능이 활성화되어 있는지 확인합니다.

- 지도 보기
- POI 검색
- 보행자 경로 안내

권한이 없으면 지도 인증, 목적지 검색 또는 경로 조회가 실패할 수 있습니다.

### 4. 빌드 및 실행

Android Studio에서 프로젝트를 연 뒤 Gradle Sync를 실행합니다. 이후 실제 기기 또는 에뮬레이터에서 앱을 실행합니다.

명령줄에서 Kotlin 컴파일을 확인하려면 다음 명령을 사용합니다.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:compileDebugKotlin
```

## 사용 흐름

1. 홈 화면에서 경로 설정 화면으로 이동합니다.
2. 위치 권한을 허용합니다.
3. 목적지 검색창에 장소명 또는 주소를 입력합니다.
4. 검색 결과에서 목적지를 선택합니다.
5. 지도에서 목적지 마커와 예상 거리를 확인합니다.
6. `이 경로로 시작` 버튼을 누릅니다.
7. 전용 경로 안내 화면에서 경로선, 남은 거리, 예상 시간, 진행률을 확인합니다.
8. 필요하면 `북쪽 고정`, `진행 방향`, `+`, `-`, `내 위치` 버튼을 사용합니다.
9. 안내를 끝내려면 `경로 안내 종료` 버튼을 누릅니다.

## 경로 안내 동작 기준

- 위치 갱신: 약 `2.5초` 또는 `3m` 이동 기준
- 경로 이탈 판정: 경로선에서 `50m` 이상 이탈
- 자동 재탐색: `3회` 연속 이탈 시 실행
- 목적지 도착 판정: 목적지 `20m` 이내 진입
- 예상 도보 시간: 평균 보행 속도 약 `80m/분` 기준

경로 안내 중 남은 거리는 매 위치 갱신마다 서버에 재요청하지 않습니다. 기존 경로선에서 현재 위치와 가장 가까운 지점을 찾고, 해당 지점부터 목적지까지의 경로 길이를 합산합니다. 경로를 벗어난 경우에만 TMap 보행자 경로 API를 다시 호출합니다.

## Android 권한

앱은 다음 권한을 사용합니다.

| 권한 | 용도 |
| --- | --- |
| `INTERNET` | TMap 지도, POI 검색, 경로 API 요청 |
| `ACCESS_NETWORK_STATE` | 네트워크 상태 확인 |
| `ACCESS_COARSE_LOCATION` | 대략적인 위치 확인 |
| `ACCESS_FINE_LOCATION` | GPS 기반 현재 위치 추적 |
| `VIBRATE` | 안내 시작, 재탐색, 도착 진동 알림 |
| `POST_NOTIFICATIONS` | 향후 알림 기능 확장을 위한 선언 |

## 프로젝트 구조

```text
app/src/main/java/com/bycho/safereturnhome/
├── navigation/                # Compose Navigation 경로
├── ui/screen/home/            # 홈 화면
├── ui/screen/map/             # 지도 설정 및 경로 안내 화면
├── ui/screen/trip/            # 기존 이동 상태 화면
├── ui/state/                  # UI 상태 모델
└── ui/viewmodel/              # 화면별 ViewModel

app/libs/
├── tmap-sdk-3.5.aar
└── vsm-tmap-sdk-v2-android-2.0.0.aar
```

## 알려진 제한 사항

- 백그라운드 위치 추적과 Foreground Service는 아직 구현되어 있지 않습니다.
- 앱이 백그라운드로 전환되면 지속 안내를 보장하지 않습니다.
- 턴바이턴 좌회전, 우회전 안내는 아직 제공하지 않습니다.
- TTS 음성은 기기의 한국어 TTS 엔진과 미디어 볼륨 설정에 영향을 받습니다.
- 방향 센서가 없는 기기에서는 GPS 이동 방향을 사용합니다.
- GPS 정확도가 낮은 실내에서는 경로 이탈 판정이 흔들릴 수 있습니다.

## 보안 주의 사항

- `TMAP_API_KEY`는 `local.properties`에만 저장합니다.
- API 키가 포함된 파일을 Git에 커밋하지 않습니다.
- 로그, 이슈, 스크린샷에 API 키가 노출되지 않도록 확인합니다.
