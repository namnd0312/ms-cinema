package com.namnd.notification.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for admin broadcast notifications. */
public record BroadcastRequestDto(
        @NotBlank String title,
        @NotBlank String message
) {}
