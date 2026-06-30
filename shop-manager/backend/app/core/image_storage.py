"""Item image storage — a small abstraction so the storage backend is
swappable without touching callers.

Today: local disk under the served static dir. Tomorrow: object storage
(S3/GCS) by adding an implementation and switching `IMAGE_STORAGE_BACKEND`
(CLAUDE.md §4). Base64-in-DB is retired.

Security (GUARDRAILS file-upload): only known image MIME types are accepted,
size is capped, and filenames are server-generated (never derived from user
input) so there is no path-traversal surface.
"""
import base64
import os
import secrets
from abc import ABC, abstractmethod

from app.core import config as config_mod

# MIME -> file extension for the formats we accept.
_ALLOWED_TYPES = {
    "image/jpeg": "jpg",
    "image/png": "png",
    "image/webp": "webp",
}


class ImageValidationError(ValueError):
    """Raised when an uploaded image is missing, malformed, too large, or an
    unsupported type. Callers map this to a 400 VALIDATION error."""


def decode_data_url(data_url: str, max_bytes: int) -> tuple[str, bytes]:
    """Parse a `data:<mime>;base64,<payload>` URL into (mime, raw_bytes).

    Validates the scheme, the MIME type, decodability, and the decoded size.
    """
    if not data_url or not isinstance(data_url, str) or not data_url.startswith("data:"):
        raise ImageValidationError("Expected a base64 data URL")
    try:
        header, b64 = data_url.split(",", 1)
    except ValueError as exc:
        raise ImageValidationError("Malformed data URL") from exc
    if ";base64" not in header:
        raise ImageValidationError("Only base64-encoded data URLs are supported")
    mime = header[len("data:"):].split(";", 1)[0].strip().lower()
    if mime not in _ALLOWED_TYPES:
        raise ImageValidationError(f"Unsupported image type: {mime or 'unknown'}")
    try:
        raw = base64.b64decode(b64, validate=True)
    except (ValueError, base64.binascii.Error) as exc:
        raise ImageValidationError("Image is not valid base64") from exc
    if not raw:
        raise ImageValidationError("Image is empty")
    if len(raw) > max_bytes:
        raise ImageValidationError(f"Image exceeds the {max_bytes} byte limit")
    return mime, raw


def _maybe_optimize(mime: str, data: bytes) -> tuple[str, bytes]:
    """Best-effort downscale/recompress when Pillow is available; otherwise
    store the validated bytes as-is (Pillow is an optional dependency)."""
    try:
        import io

        from PIL import Image
    except Exception:
        return mime, data
    try:
        img = Image.open(io.BytesIO(data))
        img.thumbnail((1000, 1000))
        out = io.BytesIO()
        if mime == "image/png":
            img.save(out, format="PNG", optimize=True)
        else:
            if img.mode in ("RGBA", "P", "LA"):
                img = img.convert("RGB")
            img.save(out, format="JPEG", quality=85, optimize=True)
            mime = "image/jpeg"
        return mime, out.getvalue()
    except Exception:
        # Never let optimization failure lose a valid upload.
        return mime, data


class ImageStorage(ABC):
    """Saves an image (as a base64 data URL) and returns a public URL."""

    @abstractmethod
    def save(self, data_url: str) -> str: ...

    @abstractmethod
    def delete(self, url: str) -> None: ...


class LocalDiskImageStorage(ImageStorage):
    """Writes images under `settings.images_dir` (which must sit beneath the
    served static dir) and returns `<image_url_prefix>/<file>`."""

    def save(self, data_url: str) -> str:
        settings = config_mod.settings
        mime, raw = decode_data_url(data_url, settings.image_max_bytes)
        mime, raw = _maybe_optimize(mime, raw)
        ext = _ALLOWED_TYPES[mime]
        filename = f"item_{secrets.token_hex(8)}.{ext}"
        os.makedirs(settings.images_dir, exist_ok=True)
        path = os.path.join(settings.images_dir, filename)
        with open(path, "wb") as fh:
            fh.write(raw)
        return f"{settings.image_url_prefix.rstrip('/')}/{filename}"

    def delete(self, url: str) -> None:
        settings = config_mod.settings
        if not url or not url.startswith(settings.image_url_prefix):
            return  # external / empty / not ours — leave it alone
        # Only ever touch a bare filename inside images_dir (no traversal).
        filename = os.path.basename(url)
        path = os.path.join(settings.images_dir, filename)
        try:
            if os.path.isfile(path):
                os.remove(path)
        except OSError:
            pass  # best-effort cleanup


def get_image_storage() -> ImageStorage:
    """Return the configured storage backend. Add object-storage backends here."""
    # Only "local" exists today; the env hook keeps callers backend-agnostic.
    return LocalDiskImageStorage()
