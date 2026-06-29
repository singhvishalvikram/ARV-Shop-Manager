"""Public catalog: visibility, safe-field exposure, and the security
guarantee that owner-only fields never leak to customers."""

OWNER_ONLY_FIELDS = {"purchase_cost", "location", "quantity"}


def _create(auth_client, **overrides):
    body = {"name": "Item", "type": "General", "price": 100, "quantity": 5}
    body.update(overrides)
    return auth_client.post("/api/v1/items", json=body).json()["data"]


def test_catalog_is_public(client):
    # No auth header -> still returns (unlike owner routes which 401).
    assert client.get("/api/v1/catalog/products").status_code == 200


def test_catalog_never_exposes_owner_only_fields(auth_client, client):
    _create(auth_client, name="Secret Cost", price=200, purchase_cost=50)
    products = client.get("/api/v1/catalog/products").json()["data"]
    assert len(products) >= 1
    for product in products:
        leaked = OWNER_ONLY_FIELDS & set(product.keys())
        assert not leaked, f"catalog leaked owner-only fields: {leaked}"


def test_catalog_only_shows_visible_items(auth_client, client):
    _create(auth_client, name="Shown")
    hidden = _create(auth_client, name="Hidden")
    auth_client.put(f"/api/v1/items/{hidden['id']}", json={"visible": False})

    names = [p["name"] for p in client.get("/api/v1/catalog/products").json()["data"]]
    assert "Shown" in names
    assert "Hidden" not in names


def test_catalog_uses_title_override(auth_client, client):
    item = _create(auth_client, name="Internal Name")
    auth_client.put(f"/api/v1/items/{item['id']}", json={"title_override": "Customer Name"})
    names = [p["name"] for p in client.get("/api/v1/catalog/products").json()["data"]]
    assert "Customer Name" in names
    assert "Internal Name" not in names


def test_catalog_computes_discount(auth_client, client):
    _create(auth_client, name="Discounted", price=80, mrp=100)
    product = next(p for p in client.get("/api/v1/catalog/products").json()["data"] if p["name"] == "Discounted")
    assert product["discount_percent"] == 20


def test_public_settings_excludes_non_public_keys(auth_client, client):
    auth_client.post("/api/v1/settings", json={"app_title": "My Shop", "secret_internal_key": "nope"})
    public = client.get("/api/v1/catalog/settings").json()["data"]
    assert public["app_title"] == "My Shop"
    assert "secret_internal_key" not in public
