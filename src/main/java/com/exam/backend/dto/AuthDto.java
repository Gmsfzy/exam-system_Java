package com.exam.backend.dto;

import com.exam.backend.domain.enums.RoleEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class AuthDto {

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {}

    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空")
            @Size(min = 3, max = 32, message = "用户名长度3-32") String username,
            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 64, message = "密码长度6-64") String password,
            String email,
            RoleEnum role) {}

    public record UserResponse(
            Long id, String username, String email, String role, LocalDateTime createdAt) {}

    public record AuthResponse(String token, UserResponse user) {}
}
