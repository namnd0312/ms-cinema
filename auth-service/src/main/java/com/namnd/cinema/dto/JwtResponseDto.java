package com.namnd.cinema.dto;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class JwtResponseDto {

    private Long id;

    private String token;

    private String type = "Bearer";

    private String email;

    private String username;

    private String name;

    private String refreshToken;

    private Collection<?extends GrantedAuthority> roles;

    public JwtResponseDto() {
    }

    public JwtResponseDto(Long id, String token, String email, String username, String name,
                          Collection<? extends GrantedAuthority> roles) {
        this.id = id;
        this.token = token;
        this.email = email;
        this.username = username;
        this.name = name;
        this.roles = roles;
    }

    public JwtResponseDto(Long id, String token, String refreshToken, String email,
                          String username, String name, Collection<? extends GrantedAuthority> roles) {
        this.id = id;
        this.token = token;
        this.refreshToken = refreshToken;
        this.email = email;
        this.username = username;
        this.name = name;
        this.roles = roles;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Collection<? extends GrantedAuthority> getRoles() {
        return roles;
    }

    public void setRoles(Collection<? extends GrantedAuthority> roles) {
        this.roles = roles;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
