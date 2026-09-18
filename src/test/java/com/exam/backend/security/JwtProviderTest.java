package com.exam.backend.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider();
        ReflectionTestUtils.setField(jwtProvider, "secret", "unit-test-secret-key-0123456789-abcdef-xxxxxx");
        ReflectionTestUtils.setField(jwtProvider, "expirationMs", 3600000L);
        jwtProvider.init();
    }

    @Test
    @DisplayName("生成并解析 token：userId/role 正确")
    void generateAndParse() {
        String token = jwtProvider.generateToken(42L, "teacher01", "teacher");

        assertThat(token).isNotBlank();
        assertThat(jwtProvider.validateToken(token)).isTrue();
        assertThat(jwtProvider.parseUserId(token)).isEqualTo(42L);
        assertThat(jwtProvider.parseRole(token)).isEqualTo("teacher");
    }

    @Test
    @DisplayName("篡改后的 token 无效")
    void tamperedTokenInvalid() {
        String token = jwtProvider.generateToken(1L, "u", "student");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(jwtProvider.validateToken(tampered)).isFalse();
    }

    @Test
    @DisplayName("非本系统签发的 token 解析抛异常")
    void foreignTokenThrows() {
        JwtProvider other = new JwtProvider();
        ReflectionTestUtils.setField(other, "secret", "another-secret-key-aaaaaaaaaaaaaaaa-bbbbbbbbbb");
        ReflectionTestUtils.setField(other, "expirationMs", 3600000L);
        other.init();

        String foreign = other.generateToken(1L, "u", "student");
        assertThat(jwtProvider.validateToken(foreign)).isFalse();
        assertThatThrownBy(() -> jwtProvider.parseUserId(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("密钥不足 32 字符时 init 抛错")
    void weakSecretRejected() {
        JwtProvider weak = new JwtProvider();
        ReflectionTestUtils.setField(weak, "secret", "short");
        assertThatThrownBy(weak::init).isInstanceOf(IllegalStateException.class);
    }
}
