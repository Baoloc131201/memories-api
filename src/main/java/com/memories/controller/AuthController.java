package com.memories.controller;

import com.memories.dto.AuthDtos.*;
import com.memories.security.AuthCookies;
import com.memories.security.JwtAuthFilter;
import com.memories.service.AuthService;
import com.memories.service.PasswordResetService;
import com.memories.service.SocialTokenVerifier;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookies authCookies;
    private final SocialTokenVerifier socialVerifier;
    private final PasswordResetService passwordReset;

    public AuthController(AuthService authService, AuthCookies authCookies, SocialTokenVerifier socialVerifier,
                          PasswordResetService passwordReset) {
        this.authService = authService;
        this.authCookies = authCookies;
        this.socialVerifier = socialVerifier;
        this.passwordReset = passwordReset;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest req, HttpServletResponse response) {
        return withCookies(authService.register(req), response);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest req, HttpServletResponse response) {
        return withCookies(authService.login(req), response);
    }

    /** Đăng nhập/đăng ký bằng Google. `credential` là ID token nhận từ Google Identity Services. */
    @PostMapping("/google")
    public UserResponse google(@Valid @RequestBody GoogleLoginRequest req, HttpServletResponse response) {
        return withCookies(authService.socialLogin(socialVerifier.verifyGoogle(req.credential())), response);
    }

    /** Đăng nhập/đăng ký bằng Facebook. `accessToken` là token nhận từ FB.login(). */
    @PostMapping("/facebook")
    public UserResponse facebook(@Valid @RequestBody FacebookLoginRequest req, HttpServletResponse response) {
        return withCookies(authService.socialLogin(socialVerifier.verifyFacebook(req.accessToken())), response);
    }

    /** Gửi link đặt lại mật khẩu. Email chưa đăng ký thì 404, gửi lại trong 60 giây thì 429. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordReset.requestReset(req.email());
    }

    /** 204 nếu link còn dùng được, 400 nếu đã dùng hoặc hết hạn. Token gửi trong body để không bị ghi vào log URL. */
    @PostMapping("/reset-password/validate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void validateResetToken(@Valid @RequestBody ResetTokenRequest req) {
        passwordReset.validate(req.token());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordReset.resetPassword(req.token(), req.password());
    }

    @GetMapping("/providers")
    public ProvidersResponse providers() {
        return new ProvidersResponse(
                socialVerifier.googleEnabled() ? socialVerifier.googleClientId() : null,
                socialVerifier.facebookEnabled() ? socialVerifier.facebookAppId() : null);
    }

    /**
     * Cấp access token mới khi access token cũ (15 phút) hết hạn. Lỗi thì không xoá cookie:
     * nếu tab khác vừa refresh xong, cookie đang giữ là cặp token mới hợp lệ.
     */
    @PostMapping("/refresh")
    public UserResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        return withCookies(authService.refresh(authCookies.readRefresh(request).orElse(null)), response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Claims claims = request.getAttribute(JwtAuthFilter.CLAIMS_ATTR) instanceof Claims c ? c : null;
        authService.logout(claims, authCookies.readRefresh(request).orElse(null));
        response.addHeader(HttpHeaders.SET_COOKIE, authCookies.clearAccess());
        response.addHeader(HttpHeaders.SET_COOKIE, authCookies.clearRefresh());
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserDetails principal) {
        return AuthService.toResponse(authService.getUser(principal.getUsername()));
    }

    private UserResponse withCookies(AuthResult result, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, authCookies.createAccess(result.accessToken()));
        response.addHeader(HttpHeaders.SET_COOKIE, authCookies.createRefresh(result.refreshToken()));
        return result.user();
    }
}
