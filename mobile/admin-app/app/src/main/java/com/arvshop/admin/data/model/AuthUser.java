package com.arvshop.admin.data.model;

import org.json.JSONObject;

/** Mirrors the auth `user` object: {id, phone, name, role}. */
public final class AuthUser {

    public final long id;
    public final String phone;
    public final String name;
    public final String role;

    public AuthUser(long id, String phone, String name, String role) {
        this.id = id;
        this.phone = phone;
        this.name = name;
        this.role = role;
    }

    public static AuthUser fromJson(JSONObject o) {
        return new AuthUser(
                o.optLong("id", -1),
                o.optString("phone", ""),
                o.optString("name", ""),
                o.optString("role", "owner"));
    }
}
