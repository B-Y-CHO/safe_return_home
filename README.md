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

- 경로 설정 화면에서 `일반 경로`와 `CCTV·가로등 참고 경로` 선택
- TMap 보행자 경로 API를 이용한 실제 경로선 표시
- CCTV·가로등 참고 경로 선택 시 구미시 학교 주변 및 생활방범 CCTV 좌표와 가로등 좌표를 참고한 보행 경로 표시
- 현재 위치와 목적지 주변 CCTV 주소부터 병렬로 TMap 좌표로 변환하고 최대 `23시간` 동안 기기 내부에 캐시
- CCTV 좌표, 출발지, 목적지로 후보 그래프를 만들고 CCTV 비보호 구간에 페널티를 주는 A* 탐색 실행
- 가로등 인접도를 자체 휴리스틱 점수에 보조 반영해 같은 CCTV 후보 중 더 밝은 구간을 우선 선택
- A*가 우회 허용 범위 안에서 선택한 CCTV 인접 지점을 최대 `3곳`까지 TMap 보행 경로 중간 좌표로 전달
- 최종 경로선 주변 CCTV는 지도에 녹색 마커로 표시
- 최종 경로선 기준 180m 안의 가로등은 지도에 노란색/주황색 마커로 표시
- 안내 시작 후 지도 중심의 전용 안내 화면으로 전환
- 현재 위치 기준 남은 경로 거리와 예상 도보 시간 갱신
- 전체 경로 대비 진행률 바 표시
- 경로 이탈 감지 및 자동 재탐색
- 목적지 도착 감지
- 안내 시작, 재탐색, 도착 시 진동 및 TTS 음성 안내
- 다음 회전 또는 이동 안내와 해당 지점까지의 거리 표시
- 다음 안내 지점 100m, 30m 전 TTS 음성 안내
- 상세 안내 응답이 없는 환경에서는 보행 경로선의 방향 전환 지점을 분석해 좌우 회전 안내 생성
- 접이식 `앞으로의 경로` 패널에서 최대 5개의 다음 안내와 경로상 거리 확인
- 안내 중 화면 자동 꺼짐 방지

### 지도 방향 모드

- 진행 방향 모드: 휴대폰이 향하는 방향이 지도 위쪽을 향하도록 회전
- 북쪽 고정 모드: 지도 위쪽을 항상 북쪽으로 고정
- 안내 화면의 버튼으로 두 모드 전환

### SOS

- 지도 화면 우측 하단에 빨간색 `SOS` 버튼 고정
- 홈 화면의 보호자 설정에서 검증된 휴대폰 번호를 저장
- 오작동 방지를 위해 `SOS` 버튼을 `3초` 동안 길게 눌러 실행
- 실행 후 `5초` 카운트다운 동안 취소 가능
- 문자 권한 승인 후 현재 위치 지도 링크가 포함된 보호자 문자를 자동 발송
- 실제 SMS 발송 결과를 확인하고 실패 원인을 화면에 표시
- 카운트다운 화면에서 `112` 전화 앱 연결 가능

`112` 전화는 전화 앱만 열며 자동으로 발신하지 않습니다. 사용자가 전화 앱에서 통화 버튼을 눌러야 연결됩니다.

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

프로젝트 루트의 `local.properties` 파일에 Android SDK 경로, TMap API 키, CCTV API 설정을 입력합니다.

```properties
sdk.dir=C\:\\Users\\<YOUR_NAME>\\AppData\\Local\\Android\\Sdk
TMAP_API_KEY=your_tmap_api_key
CCTV_API_ENDPOINT=https://apis.data.go.kr/5080000/schulCfrCctvService/getSchulCfrCctv
CCTV_CRIME_PREVENTION_API_ENDPOINT=https://apis.data.go.kr/5080000/lvlhCrmprvCctvService/getLvlhCrmprvCctv
CCTV_API_KEY=your_public_data_api_key
STREETLIGHT_API_ENDPOINT=https://api.odcloud.kr/api/15157590/v1/uddi:7265cf69-ea47-4608-ad58-e6640baf9371
STREETLIGHT_API_KEY=your_public_data_api_key
```

Windows의 `.properties` 파일에서는 드라이브 구분자와 경로 구분자를 이스케이프해야 합니다.

`local.properties`는 Git에 포함하지 마세요. API 키를 README, 소스 코드, 이슈, 커밋에 직접 작성하면 안 됩니다.

### 3. TMap API 권한 확인

TMap 콘솔에서 현재 키에 필요한 기능이 활성화되어 있는지 확인합니다.

- 지도 보기
- POI 검색
- 보행자 경로 안내
- 주소 검색

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
9. 안내를 끝내려면 `경로 안내 종료` 버튼을 누릅니다. 목적지에 도착하거나 안내를 종료하면 홈 화면으로 돌아갑니다.

## 경로 안내 동작 기준

- 위치 갱신: 약 `2.5초` 또는 `3m` 이동 기준
- 경로 이탈 판정: 경로선에서 `50m` 이상 이탈
- 자동 재탐색: `3회` 연속 이탈 시 실행
- 목적지 도착 판정: 목적지 `20m` 이내 진입
- 예상 도보 시간: 평균 보행 속도 약 `80m/분` 기준

경로 안내 중 남은 거리는 매 위치 갱신마다 서버에 재요청하지 않습니다. 기존 경로선에서 현재 위치와 가장 가까운 지점을 찾고, 해당 지점부터 목적지까지의 경로 길이를 합산합니다. 경로를 벗어난 경우에만 TMap 보행자 경로 API를 다시 호출합니다.

CCTV·가로등 참고 경로의 A* 탐색은 공공데이터의 CCTV 좌표를 기반으로 생성한 후보 그래프에서 실행됩니다. 가로등은 CCTV 후보 사이의 구간 비용을 낮추는 보조 선호값으로만 사용합니다. 공공데이터에 실제 보행 도로 노드와 간선이 포함되어 있지 않으므로, A*가 고른 CCTV 인접 지점 사이의 실제 보행 경로선은 TMap 보행 경로 API가 계산합니다. 이 경로는 공공데이터 기반 참고 경로이며 실제 안전을 보장하는 공식 점수가 아닙니다.

## Android 권한

앱은 다음 권한을 사용합니다.

| 권한 | 용도 |
| --- | --- |
| `INTERNET` | TMap 지도, POI 검색, 경로 API 요청 |
| `ACCESS_NETWORK_STATE` | 네트워크 상태 확인 |
| `ACCESS_COARSE_LOCATION` | 대략적인 위치 확인 |
| `ACCESS_FINE_LOCATION` | GPS 기반 현재 위치 추적 |
| `SEND_SMS` | 보호자에게 현재 위치가 포함된 SOS 문자 자동 발송 |
| `VIBRATE` | 안내 시작, 재탐색, 도착 진동 알림 |
| `POST_NOTIFICATIONS` | 향후 알림 기능 확장을 위한 선언 |

## 프로젝트 구조

```text
app/src/main/java/com/bycho/safereturnhome/
├── navigation/                # Compose Navigation 경로
├── ui/screen/home/            # 홈 화면
├── ui/screen/map/             # 지도 설정 및 경로 안내 화면
├── ui/state/                  # UI 상태 모델
└── ui/viewmodel/              # 화면별 ViewModel

app/libs/
├── tmap-sdk-3.5.aar
└── vsm-tmap-sdk-v2-android-2.0.0.aar
```

## 알려진 제한 사항

- 백그라운드 위치 추적과 Foreground Service는 아직 구현되어 있지 않습니다.
- 앱이 백그라운드로 전환되면 지속 안내를 보장하지 않습니다.
- TTS 음성은 기기의 한국어 TTS 엔진과 미디어 볼륨 설정에 영향을 받습니다.
- 방향 센서가 없는 기기에서는 GPS 이동 방향을 사용합니다.
- GPS 정확도가 낮은 실내에서는 경로 이탈 판정이 흔들릴 수 있습니다.

## 실기기 SOS 점검

다음 항목은 SMS 발송이 가능한 실제 Android 기기에서 확인합니다.

1. 보호자 설정에서 빈 값, 일반 전화번호, 자릿수가 잘못된 번호가 저장되지 않는지 확인합니다.
2. 올바른 보호자 휴대폰 번호를 저장합니다.
3. 문자 권한을 거부한 상태에서 SOS 버튼을 `3초` 동안 눌러 권한 필요 안내가 표시되는지 확인합니다.
4. 문자 권한을 허용하고 SOS 버튼을 `3초` 동안 눌러 `5초` 카운트다운이 표시되는지 확인합니다.
5. 카운트다운에서 취소를 눌러 문자가 발송되지 않는지 확인합니다.
6. 다시 SOS를 실행하고 카운트다운 종료 후 보호자 기기에 현재 위치 링크가 포함된 문자가 도착하는지 확인합니다.
7. 비행기 모드 등 통신 불가 상태에서 SOS를 실행해 실패 원인이 화면에 표시되는지 확인합니다.
8. `112 전화 연결`을 눌러 전화 앱만 열리고 자동 발신되지 않는지 확인합니다.

## 보안 주의 사항

- `TMAP_API_KEY`는 `local.properties`에만 저장합니다.
- API 키가 포함된 파일을 Git에 커밋하지 않습니다.
- 로그, 이슈, 스크린샷에 API 키가 노출되지 않도록 확인합니다.
