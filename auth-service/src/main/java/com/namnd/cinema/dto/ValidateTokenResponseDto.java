package com.namnd.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for POST /api/auth/validate-token.
 * Returns validity flag plus claims extracted from the JWT.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateTokenResponseDto {

    private boolean valid;
    private Long userId;
    private String email;
    private List<String> roles;
}
