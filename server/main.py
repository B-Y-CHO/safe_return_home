from __future__ import annotations

from datetime import datetime, timezone
from enum import Enum
from typing import Any

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
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




app = FastAPI(title="Dalseo AI Event Mock", version="1.0.0")
danger_zones: dict[str, DangerZone] = {}
connections: set[WebSocket] = set()


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()




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
