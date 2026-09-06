from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from datetime import datetime, timezone
from enum import Enum
from typing import Any

from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from pydantic import BaseModel, Field


class DangerZoneType(str, Enum):
    LAMP_FAULT = "LAMP_FAULT"
    DANGER_EVENT = "DANGER_EVENT"
    CCTV_BLIND_SPOT = "CCTV_BLIND_SPOT"
    PATROL_WARNING = "PATROL_WARNING"


class Severity(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class DangerZone(BaseModel):
    id: str
    type: DangerZoneType = DangerZoneType.DANGER_EVENT
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    radiusMeters: float = Field(gt=0)
    message: str
    source: str = "SIMULATION"
    severity: Severity = Severity.MEDIUM
    timestamp: str | None = None


class EmergencyChatMessage(BaseModel):
    role: str = Field(pattern="^(user|assistant)$")
    content: str = Field(min_length=1, max_length=800)


class EmergencyContext(BaseModel):
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)
    accuracyMeters: float | None = Field(default=None, ge=0)
    isNavigationActive: bool = False
    destinationName: str | None = None
    remainingRouteDistanceMeters: int | None = Field(default=None, ge=0)
    routeDeviationCount: int = Field(default=0, ge=0)
    hasVerifiedGuardian: bool = False


class EmergencyChatRequest(BaseModel):
    messages: list[EmergencyChatMessage] = Field(default_factory=list, max_length=12)
    context: EmergencyContext = Field(default_factory=EmergencyContext)


class EmergencyAnalysisResult(BaseModel):
    type: str = "UNKNOWN"
    severity: Severity = Severity.MEDIUM
    confidence: float = Field(default=0.5, ge=0, le=1)
    summary: str = "상황 확인이 필요합니다."
    recommendedAction: str = "현재 위치와 주변 안전지점을 확인하세요."
    shouldNotifyGuardian: bool = False
    shouldCreateDangerZone: bool = False
    source: str = "GEMINI_API"


class EmergencyChatResponse(BaseModel):
    assistantMessage: str
    isFinal: bool = False
    result: EmergencyAnalysisResult | None = None


app = FastAPI(title="Dalseo AI Event Mock", version="1.0.0")
danger_zones: dict[str, DangerZone] = {}
connections: set[WebSocket] = set()


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def emergency_chat_prompt(request: EmergencyChatRequest) -> str:
    conversation = "\n".join(
        f"{message.role}: {message.content}" for message in request.messages[-12:]
    )
    return f"""
You are a Korean safety assistant for a safe-return-home mobile app.
Your job is to ask the minimum number of short questions needed to understand the user's emergency situation.
Do not make a final legal, police, or medical determination.
If the user says they cannot speak, says SOS/help, or describes an immediate violent/medical threat, finish immediately.

Return only one JSON object with this exact shape:
{{
  "assistantMessage": "Korean short message to show in the app",
  "isFinal": false,
  "result": {{
    "type": "SOS_REQUEST|ROUTE_DEVIATION|STATIONARY_RISK|USER_DISTRESS|UNKNOWN",
    "severity": "LOW|MEDIUM|HIGH|CRITICAL",
    "confidence": 0.0,
    "summary": "Korean one-sentence situation summary",
    "recommendedAction": "Korean short action",
    "shouldNotifyGuardian": true,
    "shouldCreateDangerZone": true,
    "source": "GEMINI_API"
  }}
}}

Rules:
- If more information is needed, set isFinal=false and ask exactly one short question in assistantMessage.
- For the first user answer, usually ask one follow-up question before finishing.
- Do not finish on vague reports such as "someone is following me" unless the user also says they cannot speak, asks for immediate help, or describes direct violence/injury.
- If enough information is available, set isFinal=true and fill result.
- For HIGH or CRITICAL, shouldNotifyGuardian should be true.
- For CRITICAL, recommend calling 112/119.
- Keep assistantMessage under 80 Korean characters.

Device context:
{json.dumps(request.context.model_dump(), ensure_ascii=False)}

Conversation:
{conversation or "user: SOS"}
""".strip()


def extract_json_object(text: str) -> dict[str, Any]:
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("Gemini response did not contain a JSON object.")
    return json.loads(text[start : end + 1])


def user_messages(request: EmergencyChatRequest) -> list[str]:
    return [
        message.content.strip()
        for message in request.messages
        if message.role == "user" and message.content.strip()
    ]


def is_immediate_emergency(text: str) -> bool:
    lowered = text.lower()
    urgent_keywords = (
        "말하기 어려",
        "말 못",
        "도와",
        "살려",
        "sos",
        "112",
        "119",
        "흉기",
        "칼",
        "폭행",
        "공격",
        "위협",
        "다쳤",
        "피",
        "쓰러",
        "납치",
        "성추행",
        "강제",
    )
    return any(keyword in lowered for keyword in urgent_keywords)


def follow_up_question(text: str) -> str:
    if "따라" in text:
        return "상대가 얼마나 가까이 있고, 주변에 밝은 곳이나 사람이 있나요?"
    if "길" in text or "잃" in text:
        return "현재 보이는 건물이나 표지판이 있나요?"
    if "다쳤" in text or "아파" in text:
        return "움직일 수 있나요, 아니면 119가 바로 필요한가요?"
    return "가장 위험하다고 느끼는 이유를 한 문장으로 알려주세요."


def require_first_follow_up(
    request: EmergencyChatRequest,
    response: EmergencyChatResponse,
) -> EmergencyChatResponse:
    messages = user_messages(request)
    if len(messages) != 1 or is_immediate_emergency(messages[0]):
        return response
    if not response.isFinal:
        return response
    return EmergencyChatResponse(
        assistantMessage=follow_up_question(messages[0]),
        isFinal=False,
        result=None,
    )


def interaction_text(response_json: dict[str, Any]) -> str:
    output_text = response_json.get("output_text") or response_json.get("outputText")
    if isinstance(output_text, str) and output_text.strip():
        return output_text

    text_blocks: list[str] = []
    for step in response_json.get("steps", []):
        if not isinstance(step, dict):
            continue
        content = step.get("content", [])
        if isinstance(content, dict):
            content = [content]
        if not isinstance(content, list):
            continue
        for block in content:
            if not isinstance(block, dict):
                continue
            text = block.get("text")
            if block.get("type") == "text" and isinstance(text, str) and text.strip():
                text_blocks.append(text)

    if not text_blocks:
        raise ValueError("Gemini response did not contain text output.")
    return "\n".join(text_blocks)


def call_gemini_emergency_chat(request: EmergencyChatRequest) -> EmergencyChatResponse:
    api_key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("GEMINI_API_KEY is not set.")

    payload = {
        "model": os.environ.get("GEMINI_MODEL", "gemini-3.5-flash"),
        "input": emergency_chat_prompt(request),
        "generation_config": {
            "temperature": 0.2,
            "thinking_level": "low",
        },
        "response_format": {
            "type": "text",
            "mime_type": "application/json",
            "schema": {
                "type": "object",
                "properties": {
                    "assistantMessage": {"type": "string"},
                    "isFinal": {"type": "boolean"},
                    "result": {
                        "type": "object",
                        "properties": {
                            "type": {"type": "string"},
                            "severity": {
                                "type": "string",
                                "enum": ["LOW", "MEDIUM", "HIGH", "CRITICAL"],
                            },
                            "confidence": {"type": "number"},
                            "summary": {"type": "string"},
                            "recommendedAction": {"type": "string"},
                            "shouldNotifyGuardian": {"type": "boolean"},
                            "shouldCreateDangerZone": {"type": "boolean"},
                            "source": {"type": "string"},
                        },
                        "required": [
                            "type",
                            "severity",
                            "confidence",
                            "summary",
                            "recommendedAction",
                            "shouldNotifyGuardian",
                            "shouldCreateDangerZone",
                            "source",
                        ],
                    },
                },
                "required": ["assistantMessage", "isFinal", "result"],
            },
        },
    }
    body = json.dumps(payload).encode("utf-8")
    http_request = urllib.request.Request(
        "https://generativelanguage.googleapis.com/v1beta/interactions",
        data=body,
        headers={
            "Content-Type": "application/json",
            "x-goog-api-key": api_key,
        },
        method="POST",
    )
    with urllib.request.urlopen(http_request, timeout=20) as response:
        response_json = json.loads(response.read().decode("utf-8"))
    model_response = EmergencyChatResponse.model_validate(
        extract_json_object(interaction_text(response_json))
    )
    return require_first_follow_up(request, model_response)


async def broadcast(message: dict[str, Any]) -> None:
    disconnected: list[WebSocket] = []
    for socket in connections:
        try:
            await socket.send_json(message)
        except Exception:
            disconnected.append(socket)
    for socket in disconnected:
        connections.discard(socket)


@app.get("/")
async def root() -> dict[str, Any]:
    return {"status": "ok", "service": "dalseo-ai-event-mock"}


@app.get("/danger-zones", response_model=list[DangerZone])
async def get_danger_zones() -> list[DangerZone]:
    return list(danger_zones.values())


@app.delete("/danger-zones")
async def clear_danger_zones() -> dict[str, Any]:
    danger_zones.clear()
    await broadcast({"type": "danger_zones_cleared"})
    return {"status": "ok", "cleared": True}


@app.post("/events", response_model=DangerZone, status_code=201)
async def create_event(event: DangerZone) -> DangerZone:
    stored = event.model_copy(update={"timestamp": event.timestamp or utc_now()})
    danger_zones.clear()
    danger_zones[stored.id] = stored
    await broadcast({"type": "danger_zone_created", "payload": stored.model_dump()})
    return stored


@app.post("/emergency/chat", response_model=EmergencyChatResponse)
async def emergency_chat(request: EmergencyChatRequest) -> EmergencyChatResponse:
    try:
        return call_gemini_emergency_chat(request)
    except RuntimeError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise HTTPException(status_code=502, detail=detail) from error
    except Exception as error:
        raise HTTPException(status_code=502, detail=str(error)) from error


@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket) -> None:
    await websocket.accept()
    connections.add(websocket)
    await websocket.send_json({"type": "connected", "message": "event stream connected"})
    try:
        while True:
            await websocket.receive_text()
    except WebSocketDisconnect:
        connections.discard(websocket)
    except Exception:
        connections.discard(websocket)
