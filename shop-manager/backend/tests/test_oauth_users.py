"""Federated-login provisioning: find-or-create / link / synthetic phone.

Tests the pure DB logic directly (no Authlib, no network). The `client` fixture
sets up an isolated temp DB + schema; we open a connection to that same DB.
"""
import pytest


@pytest.fixture()
def conn(client):  # client builds the temp DB + schema
    import app.db as db_mod

    c = db_mod.get_connection()
    try:
        yield c
    finally:
        c.close()


def test_new_google_user_is_created_as_owner(conn):
    from app.core.oauth_users import provision_oauth_user

    uid = provision_oauth_user(conn, "google", "sub-1", "a@example.com", "Alice")
    row = conn.execute("SELECT * FROM users WHERE id = ?", (uid,)).fetchone()
    assert row["role"] == "owner"
    assert row["email"] == "a@example.com"
    assert row["auth_provider"] == "google"
    assert row["provider_sub"] == "sub-1"
    assert row["password_hash"] == ""  # cannot password-login
    assert row["phone"] == "google:sub-1"  # synthetic, unique


def test_same_identity_is_idempotent(conn):
    from app.core.oauth_users import provision_oauth_user

    first = provision_oauth_user(conn, "google", "sub-2", "b@example.com", "Bob")
    second = provision_oauth_user(conn, "google", "sub-2", "b@example.com", "Bob")
    assert first == second
    assert conn.execute("SELECT COUNT(*) AS c FROM users").fetchone()["c"] == 1


def test_google_links_to_existing_email_account(conn):
    from app.core.oauth_users import provision_oauth_user

    # A pre-existing account that happens to carry the same email.
    conn.execute(
        "INSERT INTO users (phone, name, email, password_hash, role)"
        " VALUES ('919999000011', 'Existing', 'c@example.com', 'x', 'owner')"
    )
    conn.commit()
    existing_id = conn.execute(
        "SELECT id FROM users WHERE email = 'c@example.com'"
    ).fetchone()["id"]

    uid = provision_oauth_user(conn, "google", "sub-3", "c@example.com", "Carol")
    assert uid == existing_id  # linked, not duplicated
    row = conn.execute("SELECT * FROM users WHERE id = ?", (uid,)).fetchone()
    assert row["auth_provider"] == "google"
    assert row["provider_sub"] == "sub-3"


def test_two_google_users_without_email_do_not_collide(conn):
    from app.core.oauth_users import provision_oauth_user

    a = provision_oauth_user(conn, "google", "sub-A", "", "")
    b = provision_oauth_user(conn, "google", "sub-B", "", "")
    assert a != b  # synthetic phones differ, no UNIQUE collision


def test_missing_sub_is_rejected(conn):
    from app.core.oauth_users import provision_oauth_user

    with pytest.raises(ValueError):
        provision_oauth_user(conn, "google", "", "x@example.com", "X")
