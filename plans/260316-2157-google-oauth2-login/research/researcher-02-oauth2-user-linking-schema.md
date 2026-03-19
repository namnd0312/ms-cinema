# Research: OAuth2 User Account Linking & Schema Design

**Date:** 2026-03-16 | **Context:** Google OAuth2 login for MS Cinema auth-service

## 1. Database Schema: Separate Table vs Columns

### Recommended: Separate `user_oauth_providers` Table

**Benefits:**
- **Flexibility:** Supports multiple OAuth providers per user without schema sprawl
- **Normalization:** Clean separation of auth method from user identity
- **Future-proof:** Easy to add new providers without altering user table

**Schema Pattern (Better Auth pattern):**
```sql
CREATE TABLE users (
  id BIGINT PRIMARY KEY,
  email VARCHAR(255) UNIQUE NOT NULL,
  email_verified BOOLEAN DEFAULT FALSE,
  name VARCHAR(255),
  picture_url VARCHAR(512),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_oauth_providers (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider_name VARCHAR(50) NOT NULL, -- 'google', 'github', 'github'
  provider_user_id VARCHAR(255) NOT NULL, -- sub claim from OAuth
  email VARCHAR(255), -- provider's email (may differ from user.email)
  access_token VARCHAR(2048),
  refresh_token VARCHAR(2048),
  token_expires_at TIMESTAMP,
  linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(provider_name, provider_user_id),
  UNIQUE(user_id, provider_name)
);
```

**Why this beats columns on user table:**
- User can link multiple Google accounts if provider changes email
- Password column stays only for credential provider
- No "oauth_*" column proliferation

---

## 2. Auto-Link by Email: Security Considerations

### Recommended Approach: Cautious Linking

**Only auto-link if:**
1. Email verified by OAuth provider (`email_verified: true` in Google ID token)
2. Email is exact match in existing user record
3. User explicitly consents to linking on first sign-in

**Never** auto-link on unverified emails—opens account takeover vector.

**Implementation:**
```java
// Google ID token contains: email, email_verified, sub (provider ID)
if (idTokenClaims.isEmailVerified() && existingUserByEmail != null) {
    // Show user: "Sign in as [email]?" with option to create new instead
    // Proceed only after user confirmation
    linkOAuthProviderToUser(existingUserByEmail, googleProviderId);
}
```

**Key Risk:** Provider email changes (user updates Google Account email) breaks lookup. Solution: Prioritize provider's `sub` (immutable ID) for linking, fallback to email.

---

## 3. OAuth-Only Users (No Password)

### Schema & Validation Strategy

**In user table:**
```sql
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255) DEFAULT NULL;
ALTER TABLE users ADD COLUMN auth_type VARCHAR(50) DEFAULT 'local'; -- 'local', 'oauth'
```

**For OAuth-only users:**
- `password_hash = NULL`
- `auth_type = 'oauth'` or check `user_oauth_providers` count

**Validation Logic:**
```java
// During login attempt
if (user.getPasswordHash() == null) {
    // Reject local password login
    // Redirect: "No password set. Sign in with Google or set password first."
}

// Allow password reset for OAuth users
public void resetPasswordForOAuthUser(User user) {
    // Generate temp password or verify email link
    // Set password_hash
    // User can now login with password OR OAuth
}
```

**Don't store placeholder values** ("oauth", "N/A", empty string) in password field—use NULL to indicate absence. Bcrypt hashing overhead shouldn't apply.

---

## 4. Google OAuth2 ID Token Validation (Server-Side)

### Recommended: Use Google Auth Library

**Never implement JWT validation manually.** Google recommends official libraries:

**Java:**
```java
GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
    new NetHttpTransport(),
    new GsonFactory()
)
    .setAudience(Collections.singletonList(CLIENT_ID))
    .setIssuer("https://accounts.google.com")
    .build();

GoogleIdToken idToken = verifier.verify(tokenString);
if (idToken != null) {
    GoogleIdToken.Payload payload = idToken.getPayload();
    String userId = payload.getSubject();    // unique provider ID
    String email = payload.getEmail();
    String emailVerified = payload.getEmailVerified();
    String name = payload.get("name");
    String picture = payload.get("picture");
}
```

**Verifies:**
- JWT signature (using Google's public keys, cached/refreshed)
- `aud` claim matches your CLIENT_ID
- `exp` claim not expired
- `iss` is Google (`https://accounts.google.com`)

**Don't use tokeninfo endpoint** in production—subject to rate limiting & latency.

---

## 5. CSRF Protection: State + PKCE for SPAs

### State Parameter (Client-to-Server CSRF)

**Flow:**
1. Frontend generates random 32+ byte `state` value, stores in session storage
2. Redirects to Google with state in query param
3. Google returns auth code + state
4. Frontend sends code + state to backend
5. Backend verifies state matches session → prevents CSRF

**Implementation:**
```java
@PostMapping("/auth/google/callback")
public ResponseEntity<?> googleCallback(
    @RequestParam String code,
    @RequestParam String state,
    HttpSession session
) {
    String storedState = (String) session.getAttribute("oauth_state");
    if (storedState == null || !storedState.equals(state)) {
        throw new SecurityException("CSRF: state mismatch");
    }
    // Proceed to exchange code for tokens
}
```

### PKCE (Server-to-Google Code Interception)

**Required for public clients (SPAs)** per OAuth2 2024 best practices.

**Flow:**
1. Frontend generates `code_verifier` (43-128 chars, alphanumeric+`-._~`)
2. Compute `code_challenge = SHA256(code_verifier)` base64url-encoded
3. Send auth request with `code_challenge` + `code_challenge_method=S256`
4. Google returns auth code
5. Backend exchanges code + `code_verifier` for tokens
6. Google verifies: SHA256(verifier) == challenge

**Result:** Even if attacker intercepts auth code, can't exchange without verifier.

**Key Distinction:**
- **State** = prevents CSRF (client→server)
- **PKCE** = prevents code interception (server→Google)
- **Use both** for maximum security

---

## Summary & Recommendations

| Pattern | Recommendation | Rationale |
|---------|---|---|
| User-Provider Model | Separate `user_oauth_providers` table | Scalable, clean, multi-provider ready |
| Auto-Link by Email | Yes, if provider email_verified=true + user confirms | Balance UX vs security |
| OAuth-Only Users | NULL password_hash + check provider links | No fake/sentinel values |
| Token Validation | Google Auth Library (GoogleIdTokenVerifier) | Official, battle-tested, handles key rotation |
| CSRF/Interception | State + PKCE both mandatory | State=client-side CSRF, PKCE=code interception |

---

## Sources

- [User Account Linking - Auth0](https://auth0.com/docs/manage-users/user-accounts/user-account-linking)
- [Managing Multiple Providers - OmniAuth](https://github.com/omniauth/omniauth/wiki/Managing-Multiple-Providers)
- [Complete Guide to Multi-Provider OAuth 2.0 - DEV Community](https://dev.to/rishabh570/complete-guide-to-multi-provider-oauth-2-authorization-in-nodejs-36j5)
- [Verify Google ID Token - Google Developers](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token)
- [Backend Authentication - Google Developers](https://developers.google.com/identity/sign-in/web/backend-auth)
- [Prevent CSRF with State Parameter - Auth0](https://auth0.com/docs/secure/attack-protection/state-parameters)
- [OAuth 2.0 Security Best Practices - Authgear](https://www.authgear.com/post/oauth2-security-best-practices-pkce-state)
- [What is PKCE? - Descope](https://www.descope.com/learn/post/pkce)
- [Email Verification Best Practices - SuperTokens](https://supertokens.medium.com/implementing-the-right-email-verification-flow-bba9283e1d63)
