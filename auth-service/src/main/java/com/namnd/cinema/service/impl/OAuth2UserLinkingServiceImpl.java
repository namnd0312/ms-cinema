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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.Set;

/**
 * Finds or creates users from OAuth2 provider data.
 * Lookup order: provider link -> email match -> create new user.
 */
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

        // 1. Check if provider link already exists (returning user)
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

        // 4. Create provider link (handle race condition for concurrent first-time logins)
        try {
            linkProvider(user, providerName, providerUserId, email);
        } catch (DataIntegrityViolationException e) {
            // Concurrent login already linked — re-read and return
            log.warn("OAuth2 login: concurrent link detected for provider={} sub={}, re-reading",
                providerName, providerUserId);
            return oauthProviderRepository
                .findByProviderNameAndProviderUserId(providerName, providerUserId)
                .orElseThrow(() -> new RuntimeException("OAuth2 provider link not found after conflict"))
                .getUser();
        }

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
