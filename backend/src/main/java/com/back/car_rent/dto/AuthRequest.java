package com.back.car_rent.dto;

import lombok.Data;

@Data
public class AuthRequest {
    private String username;
    private String password;
}