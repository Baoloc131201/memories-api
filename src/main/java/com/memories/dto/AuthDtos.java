package com.memories.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "^[a-zA-Z0-9_.]+$", message = "chỉ gồm chữ, số, dấu _ và .")
            String username,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Size(min = 6, max = 100) String password) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record GoogleLoginRequest(@NotBlank String credential) {}

    public record FacebookLoginRequest(@NotBlank String accessToken) {}

    /** Thông tin công khai để FE hiện nút đăng nhập; null nghĩa là chưa cấu hình. */
    public record ProvidersResponse(String googleClientId, String facebookAppId) {}

    public record UserResponse(Long id, String username, String displayName) {}

    /** Kết quả nội bộ của AuthService: các token được đặt vào cookie HttpOnly, không trả trong body. */
    public record AuthResult(String accessToken, String refreshToken, UserResponse user) {}
}
