"""Liveness/readiness probe (public)."""
from fastapi import APIRouter

from app.core.config import settings
from app.core.envelope import success

router = APIRouter(tags=["health"])


@router.get("/health")
def health():
    return success({"status": "ok", "version": settings.app_version})
