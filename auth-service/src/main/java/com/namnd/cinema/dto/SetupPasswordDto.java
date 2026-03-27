package com.namnd.cinema.dto;

import lombok.Data;

@Data
public class SetupPasswordDto {
    private String token;
    private String password;
    private String confirmPassword;
}
