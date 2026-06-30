"""The customer catalog can be served same-origin from this service (/catalog),
so it can call /api/v1 without CORS. Additive — the standalone static deploy is
unaffected."""


def test_catalog_served_same_origin(client):
    resp = client.get("/catalog/")
    assert resp.status_code == 200
    assert resp.headers["content-type"].startswith("text/html")
    # Repointed to the API with a static fallback.
    assert "apiGet" in resp.text
    assert "imgSrc" in resp.text


def test_catalog_manifest_served(client):
    assert client.get("/catalog/manifest.json").status_code == 200
