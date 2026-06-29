"""argon2id hashing behaviour (no DB / no app needed)."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.core.security import hash_password, verify_password  # noqa: E402


def test_hash_is_argon2id_and_not_plaintext():
    h = hash_password("correct horse battery staple")
    assert h.startswith("$argon2id$")
    assert "correct horse battery staple" not in h


def test_hash_is_salted_unique_per_call():
    assert hash_password("samepassword") != hash_password("samepassword")


def test_verify_accepts_correct_and_rejects_wrong():
    h = hash_password("rightpass123")
    assert verify_password(h, "rightpass123") is True
    assert verify_password(h, "wrongpass123") is False


def test_verify_never_raises_on_garbage():
    assert verify_password("not-a-valid-hash", "whatever") is False
