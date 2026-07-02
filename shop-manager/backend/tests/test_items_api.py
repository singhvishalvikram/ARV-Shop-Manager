"""Item API: auth gating + CRUD + envelope contract."""


def test_unauthenticated_requests_are_rejected(client):
    # The single most important guarantee the legacy Flask API lacked.
    assert client.get("/api/v1/items").status_code == 401
    assert client.post("/api/v1/items", json={"name": "x", "type": "y", "price": 1, "quantity": 1}).status_code == 401
    assert client.get("/api/v1/dashboard").status_code == 401


def test_create_and_get_item(auth_client):
    resp = auth_client.post(
        "/api/v1/items",
        json={"name": "LED Bulb", "type": "Lighting", "price": 100, "quantity": 5},
    )
    assert resp.status_code == 201, resp.text
    body = resp.json()
    assert body["success"] is True
    item = body["data"]
    assert item["name"] == "LED Bulb"
    assert item["stock_status"] == "in_stock"
    assert item["mrp"] == 120.0          # auto-derived price * 1.2

    got = auth_client.get(f"/api/v1/items/{item['id']}")
    assert got.status_code == 200
    assert got.json()["data"]["id"] == item["id"]


def test_out_of_stock_status(auth_client):
    resp = auth_client.post(
        "/api/v1/items",
        json={"name": "Sold Out", "type": "X", "price": 10, "quantity": 0},
    )
    assert resp.json()["data"]["stock_status"] == "out_of_stock"


def test_validation_rejects_bad_price(auth_client):
    resp = auth_client.post(
        "/api/v1/items",
        json={"name": "Bad", "type": "X", "price": -5, "quantity": 1},
    )
    assert resp.status_code == 422
    assert resp.json()["error"]["code"] == "VALIDATION_ERROR"


def test_update_and_delete(auth_client):
    created = auth_client.post(
        "/api/v1/items", json={"name": "Wire", "type": "Cable", "price": 50, "quantity": 3}
    ).json()["data"]
    upd = auth_client.put(f"/api/v1/items/{created['id']}", json={"price": 75, "quantity": 10})
    assert upd.status_code == 200
    assert upd.json()["data"]["price"] == 75

    delete = auth_client.delete(f"/api/v1/items/{created['id']}")
    assert delete.status_code == 200
    assert auth_client.get(f"/api/v1/items/{created['id']}").status_code == 404


def test_get_missing_item_returns_envelope_404(auth_client):
    resp = auth_client.get("/api/v1/items/999999")
    assert resp.status_code == 404
    assert resp.json()["error"]["code"] == "NOT_FOUND"
