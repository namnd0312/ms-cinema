package com.namnd.cinema.repository;

import com.namnd.cinema.model.UserOAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * Repository for OAuth2 provider linkage records.
 * Supports lookup by provider identity and user-provider checks.
 */
public interface UserOAuthProviderRepository extends JpaRepository<UserOAuthProvider, Long> {

    Optional<UserOAuthProvider> findByProviderNameAndProviderUserId(
        String providerName, String providerUserId);

    boolean existsByUserIdAndProviderName(Long userId, String providerName);
}
