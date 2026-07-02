"""Structured logging + request correlation (Standards: CODING_STANDARDS §4.7).

Every request gets a correlation id (propagated from `X-Request-ID` or generated)
that is attached to all log lines for that request and returned to the client in
the `X-Request-ID` response header, so a client error can be traced to its logs.

Logs are emitted as single-line JSON to stdout (12-factor; the platform ships them).
No third-party deps.
"""
import contextvars
import json
import logging
import sys
import time

request_id_ctx: contextvars.ContextVar = contextvars.ContextVar("request_id", default="-")


def get_request_id() -> str:
    return request_id_ctx.get()


def new_request_id() -> str:
    # Short, URL-safe, collision-resistant enough for correlation.
    import secrets

    return secrets.token_hex(8)


class JsonLogFormatter(logging.Formatter):
    """Render log records as one-line JSON, including the request id and any
    structured `extra` fields passed to the logger."""

    _RESERVED = set(
        logging.LogRecord("", 0, "", 0, "", (), None).__dict__.keys()
    ) | {"message", "asctime", "taskName"}

    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "ts": time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(record.created)),
            "level": record.levelname,
            "logger": record.name,
            "msg": record.getMessage(),
            "request_id": request_id_ctx.get(),
        }
        for key, value in record.__dict__.items():
            if key not in self._RESERVED and not key.startswith("_"):
                payload[key] = value
        if record.exc_info:
            payload["exc"] = self.formatException(record.exc_info)
        return json.dumps(payload, default=str)


def configure_logging(level: str = "INFO") -> None:
    """Install the JSON formatter on a stdout handler for the app + uvicorn loggers.
    Idempotent."""
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonLogFormatter())

    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(level)

    # Don't let uvicorn's access logger double-log; our middleware logs requests.
    for name in ("uvicorn.access",):
        logging.getLogger(name).handlers = []
        logging.getLogger(name).propagate = False
