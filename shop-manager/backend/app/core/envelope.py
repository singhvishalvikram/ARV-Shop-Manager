"""Universal Response Envelope (Standards: CODING_STANDARDS §4.2).

Every API response — success or failure — uses the same shape so clients
parse one contract:

    {"success": true,  "data": <payload>, "error": null}
    {"success": false, "data": null,      "error": {"code", "message", "details"}}
"""
from typing import Any


def success(data: Any = None) -> dict:
    return {"success": True, "data": data, "error": None}


def failure(code: str, message: str, details: Any = None) -> dict:
    return {
        "success": False,
        "data": None,
        "error": {"code": code, "message": message, "details": details},
    }
