# Phase 5: Testing & Validation

## Context Links
- [Plan overview](./plan.md)
- All previous phases

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Test the OAuth2 flow end-to-end and write unit tests for core services

## Key Insights
- OAuth2 flow hard to unit test fully; need integration test with mocked OAuth2 provider
- Spring Security Test provides `oauth2Login()` request postprocessor for MockMvc
- Focus unit tests on `OAuth2UserLinkingServiceImpl` logic (find/create/link)
- Manual E2E test with real Google credentials for final validation

## Requirements
### Functional
- Unit tests for OAuth2UserLinkingService (3 scenarios)
- Integration test for SecurityConfig OAuth2 endpoints
- Manual test checklist for E2E flow

### Non-Functional
- Tests don't require real Google credentials

## Related Code Files

### Create
- `auth-service/src/test/java/com/namnd/cinema/service/impl/OAuth2UserLinkingServiceImplTest.java`

### Existing test patterns
- Check `auth-service/src/test/` for existing test structure

## Implementation Steps

### 1. Unit tests for `OAuth2UserLinkingServiceImpl`

Three scenarios:
1. **Existing provider link:** returns associated user
2. **Email match + email_verified:** links provider to existing user
3. **No match:** creates new user + links provider

```java
@ExtendWith(MockitoExtension.class)
class OAuth2UserLinkingServiceImplTest {

    @Mock UserService userService;
    @Mock RoleService roleService;
    @Mock UserOAuthProviderRepository oauthProviderRepository;
    @InjectMocks OAuth2UserLinkingServiceImpl service;

    @Test
    void processOAuth2User_existingLink_returnsUser() { ... }

    @Test
    void processOAuth2User_emailMatch_linksProvider() { ... }

    @Test
    void processOAuth2User_noMatch_createsUser() { ... }

    @Test
    void processOAuth2User_unverifiedEmail_createsNewUser() { ... }
}
```

### 2. Manual E2E test checklist

**Prerequisites:**
- Google Cloud Console project with OAuth2 credentials
- Set env vars: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- Start: Eureka, Config Server, auth-service, api-gateway

**Test Cases:**

1. **New user via Google (no existing account)**
   - Navigate to `http://localhost:8080/oauth2/authorization/google`
   - Login with Google account
   - Verify: redirected to `http://localhost:4200/auth/oauth2/callback?token=...&refreshToken=...`
   - Verify: new User in DB with password=NULL, active=true, ROLE_USER
   - Verify: `user_oauth_providers` record with provider=google

2. **Existing user auto-link**
   - Register user with same email as Google account
   - Activate account
   - Login via Google
   - Verify: same User record, new `user_oauth_providers` link
   - Verify: password still set (can still login with password)

3. **Returning OAuth user**
   - Login via Google again (after test 1 or 2)
   - Verify: no new User or provider record created
   - Verify: JWT + refresh token issued

4. **Password login blocked for OAuth-only users**
   - Try POST `/api/auth/login` with email from test 1 (OAuth-only user)
   - Verify: 400 response with Google login suggestion

5. **Normal password login unaffected**
   - Login with password user not linked to Google
   - Verify: works as before

6. **JWT validation**
   - Use token from OAuth2 callback to call authenticated endpoint
   - Verify: works same as normal JWT

## Todo List
- [ ] Write unit tests for OAuth2UserLinkingServiceImpl
- [ ] Run unit tests, verify pass
- [ ] Manual E2E test: new user via Google
- [ ] Manual E2E test: existing user auto-link
- [ ] Manual E2E test: returning OAuth user
- [ ] Manual E2E test: password login blocked for OAuth-only
- [ ] Manual E2E test: normal login unaffected
- [ ] Verify compile: `mvn compile -pl auth-service`

## Success Criteria
- All unit tests pass
- All manual E2E test cases pass
- Existing auth tests still pass
- No regressions in password-based login flow

## Risk Assessment
- **Low:** MockMvc oauth2Login() might need specific Spring Security Test version
- **Mitigation:** Check Spring Boot 3.4.3 test dependency compatibility

## Security Considerations
- Ensure tokens in URL are consumed by frontend immediately and URL cleared
- Verify state parameter prevents CSRF (try replaying callback URL)
- Verify unverified Google emails don't auto-link

## Next Steps
- Frontend: implement `/auth/oauth2/callback` route to extract tokens from URL params
- Frontend: add "Sign in with Google" button that navigates to gateway OAuth2 URL
