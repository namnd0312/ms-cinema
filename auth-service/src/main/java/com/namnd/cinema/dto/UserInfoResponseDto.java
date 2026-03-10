package com.namnd.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for GET /api/users/me.
 * Returns the authenticated user's profile fetched fresh from the database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponseDto {

    private Long id;
    private String email;
    private String username;
    private String fullName;
    private List<String> roles;
}
