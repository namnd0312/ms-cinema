package com.namnd.cinema.service;

import com.namnd.cinema.model.User;

/**
 * Handles OAuth2 user lookup, creation, and provider linking.
 * Auto-links by email when provider confirms email_verified=true.
 */
public interface OAuth2UserLinkingService {

    User processOAuth2User(String providerName, String providerUserId,
                           String email, String name, boolean emailVerified);
}
