package com.namnd.cinema.dto;

import lombok.Data;

@Data
public class ResetPasswordDto {

    private String token;

    private String newPassword;
}
