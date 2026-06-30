"""The service worker is served from the root with a root scope, so it can
control the whole app (not just /static/js/)."""


def test_sw_served_from_root_with_root_scope(client):
    resp = client.get("/sw.js")
    assert resp.status_code == 200
    assert resp.headers["content-type"].startswith("application/javascript")
    # Allows scope "/" even though the file lives under /static/js.
    assert resp.headers.get("service-worker-allowed") == "/"


def test_sw_does_not_cache_api(client):
    # Guard the strategy: the SW must skip /api/ requests entirely.
    body = client.get("/sw.js").text
    assert "/api/" in body
    assert "never cache API" in body
