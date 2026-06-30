"""Test fixtures: a FastAPI TestClient bound to an isolated temp database.

The DB path is set via SHOP_DB_PATH *before* app modules import, so config
picks it up. Each test session gets a throwaway file.
"""
import os
import sys
import tempfile

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


@pytest.fixture()
def client(monkeypatch):
    tmp_dir = tempfile.mkdtemp()
    db_path = os.path.join(tmp_dir, "test_shop.db")
    monkeypatch.setenv("SHOP_DB_PATH", db_path)
    # Isolate item-image writes to a throwaway dir (never the repo static dir).
    monkeypatch.setenv("SHOP_IMAGES_DIR", os.path.join(tmp_dir, "images"))

    # Import lazily so config reads the patched env. Reload to drop any cached
    # settings from a previous test.
    import importlib

    from app.core import config as config_mod
    importlib.reload(config_mod)
    import app.db as db_mod
    importlib.reload(db_mod)
    import app.core.security as sec_mod
    importlib.reload(sec_mod)
    import app.main as main_mod
    importlib.reload(main_mod)

    db_mod.init_schema()

    from fastapi.testclient import TestClient
    with TestClient(main_mod.app) as c:
        yield c


@pytest.fixture()
def auth_client(client):
    """A client with a registered, logged-in owner and Bearer token set."""
    resp = client.post("/api/v1/auth/signup", json={"phone": "919999999999", "password": "supersecret1", "name": "Owner"})
    assert resp.status_code == 201, resp.text
    token = resp.json()["data"]["token"]
    client.headers.update({"Authorization": f"Bearer {token}"})
    return client
