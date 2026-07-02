"""Central Error Code Registry + typed application error.

Standards: CODING_STANDARDS §2.5 / §2.5.1 (Mandatory for Commercial Tier).
A single registry means every error the API can emit has one stable, documented
code that clients and logs can switch on — never an ad-hoc string.
"""


class ErrorCode:
    VALIDATION = "VALIDATION_ERROR"
    NOT_FOUND = "NOT_FOUND"
    UNAUTHORIZED = "UNAUTHORIZED"
    FORBIDDEN = "FORBIDDEN"
    CONFLICT = "CONFLICT"
    INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK"
    RATE_LIMITED = "RATE_LIMITED"
    INTERNAL = "INTERNAL_ERROR"


class AppError(Exception):
    """Raised anywhere in the app; the global handler renders it as an
    envelope with the right HTTP status. Never leak raw exceptions to clients
    (GUARDRAILS §2.6)."""

    def __init__(self, code: str, message: str, status_code: int = 400, details=None):
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code
        self.details = details
