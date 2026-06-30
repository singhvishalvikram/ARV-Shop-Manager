"""Dynamic owner PWA manifest — branded from settings, real PNG icons."""
from app.manifest import build_owner_manifest


def test_manifest_falls_back_to_generic_defaults():
    m = build_owner_manifest()
    assert m["name"] == "Shop Manager"
    assert m["theme_color"] == "#2196F3"
    assert m["icons"] and all(i["type"] == "image/png" for i in m["icons"])
    assert any(i["purpose"] == "maskable" for i in m["icons"])


def test_manifest_uses_shop_branding():
    m = build_owner_manifest(name="Vikram Store", theme_color="#123456")
    assert m["name"] == "Vikram Store"
    assert m["short_name"] == "Vikram Store"[:12]
    assert m["theme_color"] == "#123456"


def test_manifest_endpoint_serves_branded_json(auth_client, client):
    auth_client.post("/api/v1/settings", json={"app_title": "My Shop", "theme_color": "#abcdef"})
    m = client.get("/manifest.json").json()
    assert m["name"] == "My Shop"
    assert m["theme_color"] == "#abcdef"
    assert len(m["icons"]) >= 2  # not the old empty array


def test_icons_are_served():
    # PNG icons exist and are reachable from the static mount.
    from fastapi.testclient import TestClient
    import app.main as m

    with TestClient(m.app) as c:
        for size in (192, 512):
            r = c.get(f"/static/icons/icon-{size}.png")
            assert r.status_code == 200
            assert r.headers["content-type"] == "image/png"
