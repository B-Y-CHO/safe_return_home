import argparse
import json
import sys
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Send a test danger-zone event to the Safe Return Home FastAPI server."
    )
    parser.add_argument(
        "--server",
        default="http://127.0.0.1:8000",
        help="FastAPI server base URL. Default: http://127.0.0.1:8000",
    )
    parser.add_argument("--lat", type=float, required=True, help="Danger zone latitude.")
    parser.add_argument("--lon", type=float, required=True, help="Danger zone longitude.")
    parser.add_argument(
        "--radius",
        type=float,
        default=60.0,
        help="Danger zone radius in meters. Default: 60",
    )
    parser.add_argument(
        "--type",
        default="DANGER_EVENT",
        choices=["DANGER_EVENT", "LAMP_FAULT", "CCTV_BLIND_SPOT"],
        help="Danger zone type. Default: DANGER_EVENT",
    )
    parser.add_argument(
        "--severity",
        default="HIGH",
        choices=["LOW", "MEDIUM", "HIGH", "CRITICAL"],
        help="Danger severity. Default: HIGH",
    )
    parser.add_argument(
        "--message",
        default="Test danger event detected. Re-routing is required.",
        help="Message shown in the app.",
    )
    parser.add_argument(
        "--id",
        default=None,
        help="Event id. Default: generated from current timestamp.",
    )
    return parser.parse_args()


def post_event(args: argparse.Namespace) -> tuple[int, str]:
    event_id = args.id or f"test_{datetime.now(timezone.utc).strftime('%Y%m%d_%H%M%S')}"
    payload = {
        "id": event_id,
        "type": args.type,
        "latitude": args.lat,
        "longitude": args.lon,
        "radiusMeters": args.radius,
        "message": args.message,
        "source": "LOCAL_TEST",
        "severity": args.severity,
    }
    url = f"{args.server.rstrip('/')}/events"
    request = Request(
        url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urlopen(request, timeout=10) as response:
        return response.status, response.read().decode("utf-8")


def main() -> int:
    args = parse_args()
    try:
        status_code, body = post_event(args)
    except HTTPError as error:
        print(f"HTTP {error.code}: {error.read().decode('utf-8', errors='replace')}", file=sys.stderr)
        return 1
    except URLError as error:
        print(f"Connection failed: {error.reason}", file=sys.stderr)
        return 1

    print(f"Sent test danger event. HTTP {status_code}")
    print(body)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
