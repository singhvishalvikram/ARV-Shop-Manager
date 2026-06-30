"""Dashboard stats — auth gate + field parity the owner UI depends on."""


def test_dashboard_requires_auth(client):
    assert client.get("/api/v1/dashboard").status_code == 401


def test_dashboard_exposes_fields_owner_ui_renders(auth_client):
    auth_client.post(
        "/api/v1/items",
        json={"name": "Bulb", "type": "Electrical", "price": 50, "mrp": 80,
              "purchase_cost": 30, "quantity": 4},
    )
    data = auth_client.get("/api/v1/dashboard").json()["data"]
    # Every key the owner dashboard reads must be present (no silent regression).
    for key in (
        "total_items", "total_quantity", "total_stock_value", "total_stock_cost",
        "total_stock_mrp", "today_revenue", "type_breakdown", "recent_items",
    ):
        assert key in data, f"dashboard missing {key}"
    assert data["total_items"] == 1
    assert data["total_stock_mrp"] == 320  # 80 mrp * 4 qty


def test_dashboard_recent_items_surface_low_stock_first(auth_client):
    auth_client.post(
        "/api/v1/items", json={"name": "Plenty", "type": "X", "price": 10, "quantity": 99}
    )
    auth_client.post(
        "/api/v1/items", json={"name": "Scarce", "type": "X", "price": 10, "quantity": 1}
    )
    recent = auth_client.get("/api/v1/dashboard").json()["data"]["recent_items"]
    assert recent[0]["name"] == "Scarce"  # lowest stock first, for the low-stock panel
    # recent_items must carry the fields the panel renders, and nothing it does not need
    assert set(recent[0].keys()) == {"id", "name", "quantity"}
