"""Cart: auth required, add/update/remove, safe fields only."""


def test_cart_requires_auth(client):
    assert client.get("/api/v1/cart").status_code == 401


def _make_item(auth_client):
    return auth_client.post(
        "/api/v1/items", json={"name": "Bulb", "type": "Lighting", "price": 100, "quantity": 10}
    ).json()["data"]


def test_add_update_and_get_cart(auth_client):
    item = _make_item(auth_client)
    assert auth_client.post("/api/v1/cart", json={"item_id": item["id"], "qty": 2}).status_code == 200
    # Upsert: same item again changes qty rather than duplicating.
    auth_client.post("/api/v1/cart", json={"item_id": item["id"], "qty": 5})

    cart = auth_client.get("/api/v1/cart").json()["data"]
    assert len(cart) == 1
    assert cart[0]["qty"] == 5
    assert "purchase_cost" not in cart[0]


def test_remove_from_cart(auth_client):
    item = _make_item(auth_client)
    auth_client.post("/api/v1/cart", json={"item_id": item["id"], "qty": 1})
    auth_client.delete(f"/api/v1/cart/{item['id']}")
    assert auth_client.get("/api/v1/cart").json()["data"] == []
