# Phase 3: OAuth2 Success Handler & User Linking Service

## Context Links
- [Plan overview](./plan.md)
- [Phase 1: Schema](./phase-01-database-schema-oauth-provider.md)
- [Phase 2: Config](./phase-02-spring-security-oauth2-config.md)
- [Research: User linking](./research/researcher-02-oauth2-user-linking-schema.md)
- JwtService: `auth-service/src/main/java/com/namnd/cinema/service/JwtService.java`
- RefreshTokenService: `auth-service/src/main/java/com/namnd/cinema/service/RefreshTokenService.java`

## Overview
- **Priority:** P1 (core business logic)
- **Status:** pending
- **Description:** Custom OAuth2 success handler that finds/creates user, links OAuth provider, generates JWT+refresh token, redirects to frontend

## Key Insights
- Google `sub` claim is immutable user ID; use as primary lookup key
- Auto-link by email only when `email_verified=true` from Google
- New Google user with no matching email: create new User with NULL password, ROLE_USER, active=true (no activation needed for OAuth)
- Reuse existing `JwtService.generateTokenFromEmail(email, userId, roles)` for token generation
- Reuse existing `RefreshTokenService.createRefreshToken(userId)` for refresh token

## Requirements
### Functional
- On OAuth2 success: lookup by provider_name+provider_user_id first
- If not found: lookup User by email, auto-link if email_verified
- If no user: create new User (password=NULL, active=true) with ROLE_USER
- Generate JWT + refresh token (same format as normal login)
- Redirect to frontend: `{callbackUrl}?token={jwt}&refreshToken={refreshToken}`

### Non-Functional
- Transactional: user creation + provider linking atomic
- Log OAuth2 login events

## Architecture
```
OAuth2SuccessHandler.onAuthenticationSuccess()
  |
  v
OAuth2UserLinkingService.processOAuth2User(providerName, sub, email, name, emailVerified)
  |
  +-- findByProviderAndSub() --> found? return user
  |
  +-- findByEmail() --> found + emailVerified? link provider, return user
  |
  +-- createNewUser() + linkProvider() --> return user
  |
  v
Generate JWT + RefreshToken --> redirect to frontend
```

## Related Code Files

### Create
- `auth-service/src/main/java/com/namnd/cinema/config/security/OAuth2AuthenticationSuccessHandler.java`
- `auth-service/src/main/java/com/namnd/cinema/service/OAuth2UserLinkingService.java`
- `auth-service/src/main/java/com/namnd/cinema/service/impl/OAuth2UserLinkingServiceImpl.java`

### Existing (reuse, no modify)
- `JwtService.generateTokenFromEmail(email, userId, roles)`
- `RefreshTokenService.createRefreshToken(userId)`
- `UserService.findByEmail(email)`, `UserService.save(user)`
- `RoleService.findByName(name)`

## Implementation Steps

### 1. Create `OAuth2UserLinkingService` interface
File: `auth-service/src/main/java/com/namnd/cinema/service/OAuth2UserLinkingService.java`

```java
package com.namnd.cinema.service;

import com.namnd.cinema.model.User;

public interface OAuth2UserLinkingService {
    /**
     * Find or create user from OAuth2 provider data.
     * Links provider to user if not already linked.
     */
    User processOAuth2User(String providerName, String providerUserId,
                           String email, String name, boolean emailVerified);
}
```

### 2. Create `OAuth2UserLinkingServiceImpl`
File: `auth-service/src/main/java/com/namnd/cinema/service/impl/OAuth2UserLinkingServiceImpl.java`

```java
package com.namnd.cinema.service.impl;

import com.namnd.cinema.model.Role;
import com.namnd.cinema.model.User;
import com.namnd.cinema.model.UserOAuthProvider;
import com.namnd.cinema.repository.UserOAuthProviderRepository;
import com.namnd.cinema.service.OAuth2UserLinkingService;
import com.namnd.cinema.service.RoleService;
import com.namnd.cinema.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2UserLinkingServiceImpl implements OAuth2UserLinkingService {

    private final UserService userService;
    private final RoleService roleService;
    private final UserOAuthProviderRepository oauthProviderRepository;

    @Override
    @Transactional
    public User processOAuth2User(String providerName, String providerUserId,
                                   String email, String name, boolean emailVerified) {

        // 1. Check if provider link already exists
        Optional<UserOAuthProvider> existingLink = oauthProviderRepository
            .findByProviderNameAndProviderUserId(providerName, providerUserId);

        if (existingLink.isPresent()) {
            log.info("OAuth2 login: existing link for provider={} sub={}", providerName, providerUserId);
            return existingLink.get().getUser();
        }

        // 2. Try auto-link by email (only if email verified by provider)
        User user = null;
        if (emailVerified && email != null) {
            Optional<User> existingUser = userService.findByEmail(email);
            if (existingUser.isPresent()) {
                user = existingUser.get();
                log.info("OAuth2 login: auto-linking provider={} to existing user email={}",
                    providerName, email);
            }
        }

        // 3. Create new user if not found
        if (user == null) {
            user = createOAuth2User(email, name);
            log.info("OAuth2 login: created new user email={} via provider={}", email, providerName);
        }

        // 4. Create provider link
        linkProvider(user, providerName, providerUserId, email);

        return user;
    }

    private User createOAuth2User(String email, String name) {
        Role defaultRole = roleService.findByName("ROLE_USER");
        if (defaultRole == null) {
            defaultRole = new Role();
            defaultRole.setName("ROLE_USER");
            roleService.save(defaultRole);
            roleService.flush();
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(name);
        user.setUsername(email);  // use email as username for OAuth users
        user.setPassword(null);   // OAuth-only, no password
        user.setActive(true);     // no activation needed for OAuth
        user.setRoles(Set.of(defaultRole));
        userService.save(user);
        return user;
    }

    private void linkProvider(User user, String providerName, String providerUserId, String email) {
        UserOAuthProvider provider = UserOAuthProvider.builder()
            .user(user)
            .providerName(providerName)
            .providerUserId(providerUserId)
            .providerEmail(email)
            .build();
        oauthProviderRepository.save(provider);
    }
}
```

### 3. Create `OAuth2AuthenticationSuccessHandler`
File: `auth-service/src/main/java/com/namnd/cinema/config/security/OAuth2AuthenticationSuccessHandler.java`

```java
package com.namnd.cinema.config.security;

import com.namnd.cinema.model.RefreshToken;
import com.namnd.cinema.model.Role;
import com.namnd.cinema.model.User;
import com.namnd.cinema.service.JwtService;
import com.namnd.cinema.service.OAuth2UserLinkingService;
import com.namnd.cinema.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuth2UserLinkingService oAuth2UserLinkingService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Value("${namnd.app.oauth2CallbackUrl}")
    private String oauth2CallbackUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauthToken.getPrincipal();

        String providerName = oauthToken.getAuthorizedClientRegistrationId(); // "google"
        String providerUserId = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        Boolean emailVerified = oAuth2User.getAttribute("email_verified");

        // Find or create + link user
        User user = oAuth2UserLinkingService.processOAuth2User(
            providerName, providerUserId, email, name,
            Boolean.TRUE.equals(emailVerified));

        // Generate tokens (same as normal login)
        List<String> roles = user.getRoles().stream()
            .map(Role::getName)
            .toList();
        String jwt = jwtService.generateTokenFromEmail(user.getEmail(), user.getId(), roles);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        // Redirect to frontend with tokens
        String redirectUrl = UriComponentsBuilder.fromUriString(oauth2CallbackUrl)
            .queryParam("token", jwt)
            .queryParam("refreshToken", refreshToken.getToken())
            .build().toUriString();

        log.info("OAuth2 login success: email={}, redirecting to frontend", email);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
```

### 4. Guard password login for OAuth-only users
In `AuthController.login()`, add a check after finding the user: if `user.getPassword() == null`, return error suggesting OAuth login.

Add after line `User user = userOpt.get();` (around line 104):

```java
if (user.getPassword() == null) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body("This account uses Google login. Please sign in with Google.");
}
```

### 5. Guard change-password for OAuth-only users
In `AuthController.changePassword()`, after finding user, check if password is null:

```java
if (user.getPassword() == null) {
    return ResponseEntity.badRequest()
        .body("Cannot change password for OAuth-only accounts. Set a password first via forgot-password flow.");
}
```

## Todo List
- [ ] Create `OAuth2UserLinkingService` interface
- [ ] Create `OAuth2UserLinkingServiceImpl`
- [ ] Create `OAuth2AuthenticationSuccessHandler`
- [ ] Add OAuth-only guard in `AuthController.login()`
- [ ] Add OAuth-only guard in `AuthController.changePassword()`
- [ ] Compile and verify

## Success Criteria
- OAuth2 login creates new user with NULL password and ROLE_USER
- Existing user with matching email gets provider linked (if email_verified)
- JWT + refresh token generated and included in redirect URL
- Password login rejected for OAuth-only users with helpful message
- Normal password login unaffected

## Risk Assessment
- **Medium:** Google `sub` attribute name might differ; verify OAuth2User attribute keys
- **Low:** RefreshTokenService might fail if user has existing refresh token
- **Mitigation:** Test with real Google credentials; check refresh token creation logic

## Security Considerations
- Only auto-link when Google confirms `email_verified=true`
- Tokens in URL query params: short-lived JWT (15min), frontend should consume and clear URL immediately
- No Google access/refresh tokens stored server-side
- OAuth-only users cannot bypass to password login

## Next Steps
- Phase 4: API Gateway route configuration
