"""Security hardening: auth rate-limiting + security headers (incl. CSP)."""


def test_login_is_rate_limited_after_5_attempts(client):
    # Limit defaults to 5 / window; the 6th attempt from the same IP is blocked.
    for _ in range(5):
        r = client.post("/api/v1/auth/login", json={"phone": "910000000000", "password": "whatever12"})
        assert r.status_code == 401  # invalid creds, but counts as an attempt
    blocked = client.post("/api/v1/auth/login", json={"phone": "910000000000", "password": "whatever12"})
    assert blocked.status_code == 429
    assert blocked.json()["error"]["code"] == "RATE_LIMITED"


def test_signup_is_rate_limited(client):
    for i in range(5):
        client.post("/api/v1/auth/signup", json={"phone": f"9100000000{i}", "password": "strongpass1"})
    blocked = client.post("/api/v1/auth/signup", json={"phone": "919000000099", "password": "strongpass1"})
    assert blocked.status_code == 429
    assert blocked.json()["error"]["code"] == "RATE_LIMITED"


def test_security_headers_present(client):
    resp = client.get("/api/v1/health")
    assert resp.headers["x-content-type-options"] == "nosniff"
    assert resp.headers["x-frame-options"] == "DENY"
    csp = resp.headers["content-security-policy"]
    assert "default-src 'self'" in csp
    assert "frame-ancestors 'none'" in csp
