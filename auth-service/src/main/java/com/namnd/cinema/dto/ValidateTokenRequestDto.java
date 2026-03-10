package com.namnd.cinema.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for POST /api/auth/validate-token.
 */
@Data
public class ValidateTokenRequestDto {

    @NotBlank
    private String token;
}
