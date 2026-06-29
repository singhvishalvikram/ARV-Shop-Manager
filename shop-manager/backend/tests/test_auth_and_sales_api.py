"""Auth flow + sales stock decrement / insufficient-stock guard."""


def test_login_with_wrong_password_is_rejected(client):
    client.post("/api/v1/auth/signup", json={"phone": "918888888888", "password": "rightpass99"})
    bad = client.post("/api/v1/auth/login", json={"phone": "918888888888", "password": "wrongpass99"})
    assert bad.status_code == 401
    assert bad.json()["error"]["code"] == "UNAUTHORIZED"


def test_login_with_correct_password_returns_token(client):
    client.post("/api/v1/auth/signup", json={"phone": "917777777777", "password": "rightpass99"})
    ok = client.post("/api/v1/auth/login", json={"phone": "917777777777", "password": "rightpass99"})
    assert ok.status_code == 200
    assert ok.json()["data"]["token"]


def test_duplicate_signup_conflicts(client):
    client.post("/api/v1/auth/signup", json={"phone": "916666666666", "password": "rightpass99"})
    dup = client.post("/api/v1/auth/signup", json={"phone": "916666666666", "password": "rightpass99"})
    assert dup.status_code == 409
    assert dup.json()["error"]["code"] == "CONFLICT"


def test_sale_decrements_stock(auth_client):
    item = auth_client.post(
        "/api/v1/items", json={"name": "Fan", "type": "Appliance", "price": 1000, "quantity": 5}
    ).json()["data"]
    resp = auth_client.post("/api/v1/sales", json={"item_id": item["id"], "quantity": 2, "price": 1000})
    assert resp.status_code == 201
    after = auth_client.get(f"/api/v1/items/{item['id']}").json()["data"]
    assert after["quantity"] == 3


def test_oversell_is_blocked(auth_client):
    item = auth_client.post(
        "/api/v1/items", json={"name": "Switch", "type": "Electrical", "price": 20, "quantity": 1}
    ).json()["data"]
    resp = auth_client.post("/api/v1/sales", json={"item_id": item["id"], "quantity": 5, "price": 20})
    assert resp.status_code == 409
    assert resp.json()["error"]["code"] == "INSUFFICIENT_STOCK"
    # stock must be untouched after a rejected sale
    assert auth_client.get(f"/api/v1/items/{item['id']}").json()["data"]["quantity"] == 1
