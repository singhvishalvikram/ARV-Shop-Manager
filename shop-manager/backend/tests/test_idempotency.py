"""Idempotent item creation: a replayed POST (same Idempotency-Key) must not
double-insert — it returns the originally created item."""


def _count_items(auth_client):
    return len(auth_client.get("/api/v1/items").json()["data"])


def test_same_key_does_not_duplicate(auth_client):
    body = {"name": "Queued Item", "type": "X", "price": 10, "quantity": 3}
    headers = {"Idempotency-Key": "offline-key-1"}

    first = auth_client.post("/api/v1/items", json=body, headers=headers).json()["data"]
    second = auth_client.post("/api/v1/items", json=body, headers=headers).json()["data"]

    assert first["id"] == second["id"]          # same row returned
    assert _count_items(auth_client) == 1        # only one created


def test_different_keys_create_separate_items(auth_client):
    body = {"name": "Item", "type": "X", "price": 10, "quantity": 1}
    auth_client.post("/api/v1/items", json=body, headers={"Idempotency-Key": "k1"})
    auth_client.post("/api/v1/items", json=body, headers={"Idempotency-Key": "k2"})
    assert _count_items(auth_client) == 2


def test_no_key_is_not_deduplicated(auth_client):
    body = {"name": "Item", "type": "X", "price": 10, "quantity": 1}
    auth_client.post("/api/v1/items", json=body)
    auth_client.post("/api/v1/items", json=body)
    assert _count_items(auth_client) == 2
