package com.exam.backend.service;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.User;
import com.exam.backend.domain.enums.RoleEnum;
import com.exam.backend.dto.AuthDto;
import com.exam.backend.repository.UserRepository;
import com.exam.backend.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }
        // 开发阶段特例（文档 2.5）：注册允许自选 teacher/student，缺省 student；上线前收紧
        RoleEnum role = req.role() == null ? RoleEnum.student : req.role();
        User user = User.builder()
                .username(req.username())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(role)
                .build();
        userRepository.save(user);
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthDto.AuthResponse login(AuthDto.LoginRequest req) {
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_FAIL, "用户名或密码错误"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_FAIL, "用户名或密码错误");
        }
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthDto.UserResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        return toUserResponse(user);
    }

    private AuthDto.AuthResponse toAuthResponse(User user) {
        String token = jwtProvider.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        return new AuthDto.AuthResponse(token, toUserResponse(user));
    }

    private AuthDto.UserResponse toUserResponse(User u) {
        return new AuthDto.UserResponse(u.getId(), u.getUsername(), u.getEmail(),
                u.getRole().name(), u.getCreatedAt());
    }
}