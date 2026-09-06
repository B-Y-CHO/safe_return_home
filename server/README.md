# FastAPI 이벤트 mock 서버

Gazebo/ROS2 없이 Android 앱의 위험 이벤트 수신을 검증하는 서버입니다. 데이터는 메모리에 저장되므로 서버를 재시작하면 초기화됩니다.

## Windows FastAPI 서버 실행

최초 한 번 가상환경과 의존성을 설치합니다.

```powershell
cd C:\safe_return_home-main\server
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
```

서버를 실행합니다.

```powershell
cd C:\safe_return_home-main\server
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

## 앱 서버 주소 설정

- Android Emulator: `http://10.0.2.2:8000`
- 실제 Android 기기: `http://<PC IPv4 주소>:8000`
- 이번 MVP 테스트에서 사용한 주소: `http://192.168.0.66:8000`

실제 기기와 PC는 같은 Wi-Fi에 연결되어야 합니다. 연결되지 않으면 Windows 방화벽에서 TCP 8000 포트가 허용되어 있는지 확인합니다. 앱은 HTTP 주소를 기준으로 WebSocket `/ws` 주소를 자동 생성합니다.

## API

- `GET /`: 서버 상태 확인
- `GET /danger-zones`: 현재 위험구역 목록
- `POST /events`: 위험 이벤트 저장 및 WebSocket broadcast
- `WS /ws`: 실시간 이벤트 스트림

## PowerShell 이벤트 테스트

PowerShell의 `curl` 별칭 차이를 피하기 위해 `curl.exe`를 사용합니다.

### 불량 가로등 이벤트

```powershell
curl.exe -X POST "http://127.0.0.1:8000/events" `
  -H "Content-Type: application/json" `
  -d "{`"id`":`"lamp_fault_001`",`"type`":`"LAMP_FAULT`",`"latitude`":35.82912,`"longitude`":128.53244,`"radiusMeters`":40.0,`"message`":`"UGV가 불량 가로등 구간을 감지했습니다.`",`"source`":`"UGV_01`",`"severity`":`"MEDIUM`"}"
```

### 위험 이벤트

```powershell
curl.exe -X POST "http://127.0.0.1:8000/events" `
  -H "Content-Type: application/json" `
  -d "{`"id`":`"danger_001`",`"type`":`"DANGER_EVENT`",`"latitude`":35.82960,`"longitude`":128.53320,`"radiusMeters`":60.0,`"message`":`"위험 이벤트가 감지되어 우회 경로를 안내합니다.`",`"source`":`"UGV_01`",`"severity`":`"HIGH`"}"
```

## 실제 기기 MVP 테스트 완료 내용

- 실제 Android 기기에서 FastAPI 서버 접속 확인
- `LAMP_FAULT` 이벤트 수신 확인
- `DANGER_EVENT` 이벤트 수신 확인
- 지도 위험구역 표시 확인
- Raspberry Pi 없이 FastAPI mock 서버 기반 UGV 이벤트 연동 확인

## 다음 단계

Ubuntu의 Gazebo/ROS2 bridge가 로봇 감지 결과를 동일한 `POST /events` JSON 스키마로 전송하도록 구현합니다. 이 계약을 유지하면 Android 앱의 통신 구조를 변경하지 않고 시뮬레이션과 실제 로봇 이벤트 소스를 연결할 수 있습니다.
