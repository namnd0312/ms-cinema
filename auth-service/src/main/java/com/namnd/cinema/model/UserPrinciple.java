package com.namnd.cinema.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class UserPrinciple implements UserDetails {

    private static final long serialVersionUID = 1L;

    private Long id;

    // The display name (username field from User entity)
    private String displayName;

    // Email is used as the Spring Security principal
    private String email;

    private String password;

    private boolean active;

    private boolean accountNonLocked;

    private Collection<? extends GrantedAuthority> roles;

    public UserPrinciple(Long id, String displayName, String email, String password,
                         boolean active, boolean accountNonLocked, Collection<? extends GrantedAuthority> roles) {
        this.id = id;
        this.displayName = displayName;
        this.email = email;
        this.password = password;
        this.active = active;
        this.accountNonLocked = accountNonLocked;
        this.roles = roles;
    }

    public UserPrinciple() {
    }

    public static UserPrinciple build(User user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toList());

        boolean accountNonLocked = user.getLockTime() == null;

        return new UserPrinciple(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                user.isActive(),
                accountNonLocked,
                authorities);
    }

    /**
     * Returns email as the Spring Security principal identifier.
     * Authentication is email-based.
     */
    @Override
    public String getUsername() {
        return email;
    }

    /**
     * Returns the display name (username field from User entity).
     */
    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserPrinciple user = (UserPrinciple) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
