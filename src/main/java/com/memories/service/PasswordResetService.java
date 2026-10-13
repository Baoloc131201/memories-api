package com.memories.service;

import com.memories.entity.PasswordResetToken;
import com.memories.entity.User;
import com.memories.repository.PasswordResetTokenRepository;
import com.memories.repository.UserRepository;
import com.memories.security.Tokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** Quên mật khẩu: gửi link chứa token ngẫu nhiên qua email, đổi mật khẩu khi user mở link. */
@Service
public class PasswordResetService {

    private static final Duration TTL = Duration.ofMinutes(30);
    /** Mỗi user chỉ được gửi 1 email trong khoảng này, tránh bị spam hộp thư. */
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final RefreshTokenService refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final MailService mail;
    private final String frontendUrl;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
                                RefreshTokenService refreshTokens, PasswordEncoder passwordEncoder,
                                MailService mail, @Value("${app.frontend-url}") String frontendUrl) {
        this.users = users;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.mail = mail;
        // Link trong email lấy từ cấu hình, không lấy từ header request (tránh bị giả Host/Origin để gửi link lừa đảo)
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    /** Gửi link đặt lại mật khẩu. Email chưa đăng ký thì báo 404, gửi lại quá sớm thì báo 429. */
    @Transactional
    public void requestReset(String email) {
        Instant now = Instant.now();
        User user = users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Email này không tồn tại, bạn vui lòng nhập email lại nhé"));
        if (tokens.existsByUserAndCreatedAtAfter(user, now.minus(RESEND_COOLDOWN))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Link vừa được gửi, bạn vui lòng kiểm tra hộp thư hoặc đợi " + RESEND_COOLDOWN.toSeconds() + " giây rồi thử lại nhé");
        }
        // Chỉ link mới nhất còn dùng được
        tokens.deleteByUser(user);
        String raw = Tokens.random();
        tokens.save(new PasswordResetToken(Tokens.sha256(raw), user, now.plus(TTL)));
        String link = UriComponentsBuilder.fromHttpUrl(frontendUrl).path("/reset-password")
                .queryParam("token", raw).toUriString();
        mail.sendPasswordReset(user.getEmail(), user.getDisplayName(), link, (int) TTL.toMinutes());
    }

    /** Kiểm tra link còn dùng được không (FE gọi khi mở trang đặt lại mật khẩu). */
    public void validate(String rawToken) {
        findValid(rawToken);
    }

    /** Đổi mật khẩu bằng token trong link. Thành công thì link hết hiệu lực và mọi thiết bị bị đăng xuất. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        User user = findValid(rawToken).getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        users.save(user);
        tokens.deleteByUser(user);
        refreshTokens.revokeAll(user);
    }

    private PasswordResetToken findValid(String rawToken) {
        return tokens.findByTokenHash(Tokens.sha256(rawToken))
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Link đặt lại mật khẩu đã được sử dụng hoặc đã hết hạn"));
    }

    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void purgeExpired() {
        tokens.deleteExpired(Instant.now());
    }
}
