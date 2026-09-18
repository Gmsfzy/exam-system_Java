package com.exam.backend.service;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.domain.entity.User;
import com.exam.backend.domain.enums.RoleEnum;
import com.exam.backend.dto.AuthDto;
import com.exam.backend.repository.UserRepository;
import com.exam.backend.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("注册：默认角色为 student，密码加密后落库")
    void register_defaultsToStudent_andEncodesPassword() {
        when(userRepository.existsByUsername("newbie")).thenReturn(false);
        when(passwordEncoder.encode("123123")).thenReturn("ENC_PWD");
        when(jwtProvider.generateToken(any(), eq("newbie"), eq("student"))).thenReturn("jwt");
        // 模拟 IDENTITY 回填
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(7L);
            return u;
        });

        AuthDto.AuthResponse resp = authService.register(
                new AuthDto.RegisterRequest("newbie", "123123", "n@x.com", null));

        assertThat(resp.token()).isEqualTo("jwt");
        assertThat(resp.user().username()).isEqualTo("newbie");
        assertThat(resp.user().role()).isEqualTo("student");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("ENC_PWD");
        assertThat(captor.getValue().getRole()).isEqualTo(RoleEnum.student);
    }

    @Test
    @DisplayName("注册：用户名冲突抛 409")
    void register_duplicateThrows() {
        when(userRepository.existsByUsername("teacher")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new AuthDto.RegisterRequest("teacher", "123123", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("登录：密码正确返回 token")
    void login_success() {
        User user = User.builder()
                .username("teacher").email("t@x.com")
                .passwordHash("ENC").role(RoleEnum.teacher).build();
        user.setId(1L);
        when(userRepository.findByUsername("teacher")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123123", "ENC")).thenReturn(true);
        when(jwtProvider.generateToken(1L, "teacher", "teacher")).thenReturn("token-abc");

        AuthDto.AuthResponse resp = authService.login(new AuthDto.LoginRequest("teacher", "123123"));

        assertThat(resp.token()).isEqualTo("token-abc");
        assertThat(resp.user().role()).isEqualTo("teacher");
    }

    @Test
    @DisplayName("登录：用户不存在抛 401")
    void login_userNotFound() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new AuthDto.LoginRequest("ghost", "x")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    @DisplayName("登录：密码错误抛 401")
    void login_wrongPassword() {
        User user = User.builder().username("teacher").passwordHash("ENC")
                .role(RoleEnum.teacher).build();
        user.setId(1L);
        when(userRepository.findByUsername("teacher")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new AuthDto.LoginRequest("teacher", "bad")))
                .isInstanceOf(BusinessException.class);
        verify(jwtProvider, never()).generateToken(anyLong(), anyString(), anyString());
    }
}
