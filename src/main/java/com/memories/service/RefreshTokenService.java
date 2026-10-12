package com.memories.service;

import com.memories.entity.RefreshToken;
import com.memories.entity.User;
import com.memories.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    /** Token vừa được dùng trong khoảng này thì coi là nhiều tab refresh cùng lúc, không phải bị đánh cắp. */
    private static final Duration REUSE_GRACE = Duration.ofSeconds(30);

    private final RefreshTokenRepository tokens;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens,
                               @Value("${app.jwt.refresh-expiration-ms}") long ttlMs) {
        this.tokens = tokens;
        this.ttl = Duration.ofMillis(ttlMs);
    }

    /** Cấp refresh token cho một phiên đăng nhập mới. Trả về chuỗi gốc để đặt vào cookie. */
    @Transactional
    public String issue(User user) {
        return create(user, UUID.randomUUID().toString());
    }

    /** Đổi refresh token cũ lấy token mới (rotation). Rỗng nếu token không hợp lệ, hết hạn hoặc đã bị dùng. */
    @Transactional
    public Optional<Rotated> rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        Optional<RefreshToken> found = tokens.findByTokenHash(hash(rawToken));
        if (found.isEmpty()) return Optional.empty();

        RefreshToken t = found.get();
        Instant now = Instant.now();
        if (t.getExpiresAt().isBefore(now)) return Optional.empty();

        if (tokens.markUsed(t.getId(), now) == 0) {
            // Token đã được dùng. Dùng lại muộn hơn REUSE_GRACE nghĩa là token có thể đã bị lộ → thu hồi cả phiên.
            Instant usedAt = t.getUsedAt();
            if (usedAt != null && usedAt.plus(REUSE_GRACE).isBefore(now)) {
                tokens.deleteFamily(t.getFamilyId());
            }
            return Optional.empty();
        }
        return Optional.of(new Rotated(t.getUser(), create(t.getUser(), t.getFamilyId())));
    }

    /** Thu hồi cả phiên đăng nhập chứa token này (dùng khi logout). */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        tokens.findByTokenHash(hash(rawToken)).ifPresent(t -> tokens.deleteFamily(t.getFamilyId()));
    }

    public void purgeExpired() {
        tokens.deleteExpired(Instant.now());
    }

    private String create(User user, String familyId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RefreshToken(hash(raw), familyId, user, Instant.now().plus(ttl)));
        return raw;
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Rotated(User user, String refreshToken) {}
}
