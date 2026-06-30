"""Image storage: data-URL validation, local-disk round-trip, and the item
API persisting / clearing images. Writes go to a temp dir (see conftest)."""
import os

import pytest

from app.core.image_storage import (
    ImageValidationError,
    decode_data_url,
    get_image_storage,
)

# A 1x1 transparent PNG as a base64 data URL.
PNG_1X1 = (
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR4"
    "2mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
)


def test_decode_valid_png_returns_mime_and_bytes():
    mime, raw = decode_data_url(PNG_1X1, 10_000)
    assert mime == "image/png"
    assert len(raw) > 0


@pytest.mark.parametrize("bad", [
    "not-a-data-url",
    "data:image/gif;base64,AAAA",            # unsupported type
    "data:image/png;base64,@@@not-base64@@@",  # undecodable
    "data:image/png,plain",                   # not base64
])
def test_decode_rejects_bad_input(bad):
    with pytest.raises(ImageValidationError):
        decode_data_url(bad, 10_000)


def test_decode_enforces_size_limit():
    with pytest.raises(ImageValidationError):
        decode_data_url(PNG_1X1, 5)  # decoded PNG is larger than 5 bytes


def test_local_storage_save_then_delete(client):
    from app.core import config

    storage = get_image_storage()
    url = storage.save(PNG_1X1)
    assert url.startswith("/static/images/items/")
    path = os.path.join(config.settings.images_dir, os.path.basename(url))
    assert os.path.isfile(path)

    storage.delete(url)
    assert not os.path.isfile(path)


def test_delete_ignores_foreign_urls(client):
    # Must never touch anything outside our prefix (no traversal / external URLs).
    get_image_storage().delete("https://example.com/evil.png")
    get_image_storage().delete("")


def test_create_item_persists_image(auth_client):
    from app.core import config

    data = auth_client.post(
        "/api/v1/items",
        json={"name": "Cam", "type": "X", "price": 10, "quantity": 1, "image_base64": PNG_1X1},
    ).json()["data"]
    assert data["image_url"].startswith("/static/images/items/")
    assert os.path.isfile(
        os.path.join(config.settings.images_dir, os.path.basename(data["image_url"]))
    )


def test_create_item_rejects_invalid_image(auth_client):
    resp = auth_client.post(
        "/api/v1/items",
        json={"name": "Bad", "type": "X", "price": 10, "quantity": 1,
              "image_base64": "data:text/plain;base64,aGk="},
    )
    assert resp.status_code == 400
    assert resp.json()["error"]["code"] == "VALIDATION_ERROR"


def test_update_item_can_clear_image(auth_client):
    item = auth_client.post(
        "/api/v1/items",
        json={"name": "C", "type": "X", "price": 10, "quantity": 1, "image_base64": PNG_1X1},
    ).json()["data"]
    assert item["image_url"]
    updated = auth_client.put(
        f"/api/v1/items/{item['id']}", json={"image_url": ""}
    ).json()["data"]
    assert updated["image_url"] == ""
