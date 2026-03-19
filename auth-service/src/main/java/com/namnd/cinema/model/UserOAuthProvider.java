package com.namnd.cinema.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

/**
 * Stores OAuth2 provider linkage to users.
 * Supports multiple providers per user (Google, GitHub, etc.)
 * Unique constraints prevent duplicate links.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_oauth_providers",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"provider_name", "provider_user_id"}),
        @UniqueConstraint(columnNames = {"user_id", "provider_name"})
    })
public class UserOAuthProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;  // "google", "github"

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;  // Google "sub" claim (immutable)

    @Column(name = "provider_email")
    private String providerEmail;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    @PrePersist
    protected void onCreate() {
        linkedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }
}
