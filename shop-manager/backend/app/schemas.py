"""Pydantic models — the single source of truth for API validation.

Standards: CODING_STANDARDS §1.10 (Shared Schema Source of Truth),
GUARDRAILS §2.1 (Zero-Trust input validation). Validation lives here, not
scattered through route handlers.
"""
from typing import Optional

from pydantic import BaseModel, Field


# ── Items ─────────────────────────────────────────────────
class ItemCreate(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    type: str = Field(min_length=1, max_length=100)
    price: float = Field(gt=0)
    quantity: int = Field(ge=0)
    description: str = Field(default="", max_length=2000)
    mrp: Optional[float] = Field(default=None, ge=0)
    purchase_cost: Optional[float] = Field(default=None, ge=0)
    location: str = Field(default="", max_length=200)
    image_base64: Optional[str] = None


class ItemUpdate(BaseModel):
    name: Optional[str] = Field(default=None, min_length=1, max_length=200)
    type: Optional[str] = Field(default=None, min_length=1, max_length=100)
    price: Optional[float] = Field(default=None, gt=0)
    quantity: Optional[int] = Field(default=None, ge=0)
    description: Optional[str] = Field(default=None, max_length=2000)
    mrp: Optional[float] = Field(default=None, ge=0)
    purchase_cost: Optional[float] = Field(default=None, ge=0)
    location: Optional[str] = Field(default=None, max_length=200)
    image_base64: Optional[str] = None
    image_url: Optional[str] = None


# ── Sales ─────────────────────────────────────────────────
class SaleCreate(BaseModel):
    item_id: int = Field(gt=0)
    quantity: int = Field(gt=0)
    price: float = Field(ge=0)
    description: str = Field(default="", max_length=500)


# ── Auth ──────────────────────────────────────────────────
class SignupRequest(BaseModel):
    phone: str = Field(min_length=4, max_length=20)
    password: str = Field(min_length=8, max_length=128)
    name: str = Field(default="", max_length=100)


class LoginRequest(BaseModel):
    phone: str = Field(min_length=4, max_length=20)
    password: str = Field(min_length=1, max_length=128)
