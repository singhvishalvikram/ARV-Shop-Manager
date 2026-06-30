# 2. Google Sign-In setup (OAuth client)

"Sign in with Google" is built and **feature-flagged off** until you provide an OAuth
client. The code (Authlib OIDC) loads only when the env vars below are all set, so nothing
breaks while it's unset. See [ADR-004](../architecture/adr-004-auth-fastapi-authlib-google-oidc.md).

## Steps (Google Cloud Console)

1. Go to <https://console.cloud.google.com/> → create/select a project.
2. **APIs & Services → OAuth consent screen**: choose **External**, fill app name + support
   email, add yourself as a test user (you can publish later).
3. **APIs & Services → Credentials → Create Credentials → OAuth client ID**:
   - Application type: **Web application**.
   - **Authorized redirect URI** — must match exactly:
     ```
     https://YOUR_DOMAIN/api/v1/auth/google/callback
     ```
     For local testing you can add `http://localhost:8080/api/v1/auth/google/callback`.
4. Copy the **Client ID** and **Client secret**.

## Configure the app

Copy `.env.example` → `.env` (never commit `.env`) and set:

```dotenv
AUTH_SECRET=<run: python -c "import secrets;print(secrets.token_urlsafe(48))">
GOOGLE_CLIENT_ID=<from console>
GOOGLE_CLIENT_SECRET=<from console>
GOOGLE_REDIRECT_URI=https://YOUR_DOMAIN/api/v1/auth/google/callback
OAUTH_SUCCESS_REDIRECT=https://YOUR_DOMAIN/      # where to land after login
```

`AUTH_SECRET` is required (it signs the OAuth state cookie); Google login stays **disabled**
until all four Google/redirect values + `AUTH_SECRET` are present.

The dependency is already declared (`authlib`, `itsdangerous`); a normal
`pip install -r requirements.txt` covers it.

## Verify

1. Restart the service. On startup the Google routes mount (no error = good).
2. Visit `https://YOUR_DOMAIN/api/v1/auth/google/login` → Google consent → redirected back to
   `OAUTH_SUCCESS_REDIRECT` with `#token=...` in the URL; the owner app captures it and logs
   you in.
3. A new Google user is created as an **owner**; an existing account with the same email is
   linked (no duplicate). Password login keeps working alongside.

## Notes

- Use **HTTPS** for the redirect URI in production (Google requires it; the state cookie is
  `https_only`).
- Rotate the client secret from the console if it ever leaks; only `.env`/secret store holds
  it — never source.
