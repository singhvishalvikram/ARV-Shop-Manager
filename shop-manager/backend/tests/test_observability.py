"""Request correlation IDs + structured JSON logging."""
import json
import logging


def test_response_has_request_id_header(client):
    resp = client.get("/api/v1/health")
    assert resp.headers.get("x-request-id")


def test_incoming_request_id_is_propagated(client):
    resp = client.get("/api/v1/health", headers={"X-Request-ID": "trace-abc123"})
    assert resp.headers["x-request-id"] == "trace-abc123"


def test_unsafe_request_id_is_replaced(client):
    # Injection-y value must not be echoed back verbatim.
    resp = client.get("/api/v1/health", headers={"X-Request-ID": "bad id\nwith spaces"})
    assert resp.headers["x-request-id"] != "bad id\nwith spaces"
    assert resp.headers["x-request-id"]


def test_json_formatter_emits_request_id_and_extras():
    from app.core.observability import JsonLogFormatter, request_id_ctx

    token = request_id_ctx.set("rid-xyz")
    try:
        record = logging.LogRecord(
            "shop_manager", logging.INFO, __file__, 1, "request", (), None
        )
        record.event = "request"
        record.status = 200
        line = JsonLogFormatter().format(record)
        parsed = json.loads(line)  # must be valid JSON
        assert parsed["request_id"] == "rid-xyz"
        assert parsed["msg"] == "request"
        assert parsed["status"] == 200
        assert parsed["level"] == "INFO"
    finally:
        request_id_ctx.reset(token)
