package com.memories.service;

import com.memories.dto.AuthDtos.*;
import com.memories.entity.RevokedToken;
import com.memories.entity.SocialAccount;
import com.memories.entity.User;
import com.memories.repository.RevokedTokenRepository;
import com.memories.repository.SocialAccountRepository;
import com.memories.repository.UserRepository;
import com.memories.security.JwtService;
import com.memories.service.SocialTokenVerifier.SocialProfile;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final RevokedTokenRepository revokedTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final SocialAccountRepository socialAccounts;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, RevokedTokenRepository revokedTokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokens, SocialAccountRepository socialAccounts) {
        this.users = users;
        this.socialAccounts = socialAccounts;
        this.revokedTokens = revokedTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
    }

    public AuthResult register(RegisterRequest req) {
        String username = req.username().trim().toLowerCase();
        if (users.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã tồn tại");
        }
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(req.displayName().trim());
        user.setPassword(passwordEncoder.encode(req.password()));
        users.save(user);
        return startSession(user);
    }

    public AuthResult login(LoginRequest req) {
        User user = users.findByUsername(req.username().trim().toLowerCase())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Sai tên đăng nhập hoặc mật khẩu"));
        return startSession(user);
    }

    /**
     * Đăng nhập bằng Google/Facebook. Lần đầu tự tạo user mới; các lần sau đăng nhập vào user đã liên kết.
     * Không tự gộp với user có sẵn theo email, để tránh chiếm tài khoản người khác.
     */
    @Transactional
    public AuthResult socialLogin(SocialProfile profile) {
        User user = socialAccounts.findByProviderAndProviderUserId(profile.provider(), profile.id())
                .map(SocialAccount::getUser)
                .orElseGet(() -> createSocialUser(profile));
        return startSession(user);
    }

    private User createSocialUser(SocialProfile profile) {
        User user = new User();
        user.setUsername(uniqueUsername(profile));
        String name = profile.name() != null && !profile.name().isBlank() ? profile.name().trim() : user.getUsername();
        user.setDisplayName(name.length() > 100 ? name.substring(0, 100) : name);
        // Mật khẩu ngẫu nhiên không ai biết: user này chỉ đăng nhập được qua Google/Facebook
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        users.save(user);
        socialAccounts.save(new SocialAccount(profile.provider(), profile.id(), user, profile.email()));
        return user;
    }

    /** Tạo username từ phần trước @ của email (hoặc tên), bỏ dấu tiếng Việt, thêm số nếu bị trùng. */
    private String uniqueUsername(SocialProfile profile) {
        String source = profile.email() != null ? profile.email().split("@")[0] : profile.name();
        String base = source == null ? "" : Normalizer.normalize(source, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase()
                .replaceAll("[^a-z0-9_.]", "");
        if (base.length() < 3) base = "user" + base;
        if (base.length() > 40) base = base.substring(0, 40);

        String candidate = base;
        while (users.existsByUsername(candidate)) {
            candidate = base + (1000 + random.nextInt(9000));
        }
        return candidate;
    }

    /** Đổi refresh token lấy cặp access + refresh token mới. */
    public AuthResult refresh(String refreshToken) {
        return refreshTokens.rotate(refreshToken)
                .map(r -> new AuthResult(jwtService.generate(r.user().getUsername()), r.refreshToken(), toResponse(r.user())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại"));
    }

    /** Chặn access token hiện tại (nếu còn hạn) và thu hồi refresh token của phiên. */
    public void logout(Claims accessClaims, String refreshToken) {
        if (accessClaims != null) {
            revokedTokens.save(new RevokedToken(accessClaims.getId(), accessClaims.getExpiration().toInstant()));
        }
        refreshTokens.revoke(refreshToken);
    }

    public User getUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private AuthResult startSession(User user) {
        return new AuthResult(jwtService.generate(user.getUsername()), refreshTokens.issue(user), toResponse(user));
    }

    public static UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getDisplayName());
    }

    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void purgeExpiredTokens() {
        revokedTokens.deleteExpired(Instant.now());
        refreshTokens.purgeExpired();
    }
}
