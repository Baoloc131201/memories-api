package com.memories.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memories.entity.SocialAccount.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.web.util.UriBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Xác minh token mà FE nhận từ Google/Facebook. Không tin dữ liệu FE gửi lên: mọi thông tin user
 * (id, email, tên) đều lấy từ token đã được kiểm tra chữ ký hoặc hỏi lại trực tiếp Facebook.
 */
@Service
public class SocialTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(SocialTokenVerifier.class);
    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final String googleClientId;
    private final String facebookAppId;
    private final String facebookAppSecret;
    private final NimbusJwtDecoder googleDecoder;
    private final RestClient facebook = RestClient.create("https://graph.facebook.com");
    private final ObjectMapper json;

    public SocialTokenVerifier(@Value("${app.oauth.google.client-id}") String googleClientId,
                               @Value("${app.oauth.facebook.app-id}") String facebookAppId,
                               @Value("${app.oauth.facebook.app-secret}") String facebookAppSecret,
                               ObjectMapper json) {
        this.json = json;
        this.googleClientId = googleClientId;
        this.facebookAppId = facebookAppId;
        this.facebookAppSecret = facebookAppSecret;
        this.googleDecoder = googleEnabled() ? buildGoogleDecoder() : null;
    }

    public boolean googleEnabled() {
        return !googleClientId.isBlank();
    }

    public boolean facebookEnabled() {
        return !facebookAppId.isBlank() && !facebookAppSecret.isBlank();
    }

    public String googleClientId() { return googleClientId; }
    public String facebookAppId() { return facebookAppId; }

    /** Google ID token (JWT) từ nút "Đăng nhập với Google". */
    public SocialProfile verifyGoogle(String credential) {
        if (!googleEnabled()) throw notConfigured("Google");
        Jwt jwt;
        try {
            jwt = googleDecoder.decode(credential);
        } catch (JwtException e) {
            log.warn("Google ID token không hợp lệ: {}", e.getMessage());
            throw invalid("Google");
        }
        String email = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")) ? jwt.getClaimAsString("email") : null;
        return new SocialProfile(Provider.GOOGLE, jwt.getSubject(), email, jwt.getClaimAsString("name"));
    }

    /** Facebook user access token từ FB.login(). */
    @SuppressWarnings("unchecked")
    public SocialProfile verifyFacebook(String accessToken) {
        if (!facebookEnabled()) throw notConfigured("Facebook");
        try {
            // Token phải được cấp cho chính app của mình, không phải app khác
            Map<String, Object> debug = facebookGet(b -> b.path("/debug_token")
                    .queryParam("input_token", accessToken)
                    .queryParam("access_token", facebookAppId + "|" + facebookAppSecret)
                    .build());
            Map<String, Object> data = (Map<String, Object>) debug.get("data");
            if (data == null || !Boolean.TRUE.equals(data.get("is_valid"))
                    || !facebookAppId.equals(String.valueOf(data.get("app_id")))) {
                log.warn("Facebook token không hợp lệ hoặc không thuộc app này: {}", data);
                throw invalid("Facebook");
            }

            Map<String, Object> me = facebookGet(b -> b.path("/me")
                    .queryParam("fields", "id,name,email")
                    .queryParam("access_token", accessToken)
                    .queryParam("appsecret_proof", appSecretProof(accessToken))
                    .build());
            if (!String.valueOf(data.get("user_id")).equals(String.valueOf(me.get("id")))) {
                log.warn("Facebook user_id không khớp giữa debug_token và /me");
                throw invalid("Facebook");
            }
            return new SocialProfile(Provider.FACEBOOK, String.valueOf(me.get("id")),
                    (String) me.get("email"), (String) me.get("name"));
        } catch (RestClientException | JsonProcessingException e) {
            log.warn("Gọi Facebook Graph API thất bại: {}", e.getMessage());
            throw invalid("Facebook");
        }
    }

    /** Graph API trả JSON với Content-Type `text/javascript`, nên đọc dạng chuỗi rồi tự parse. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> facebookGet(Function<UriBuilder, URI> uri) throws JsonProcessingException {
        String body = facebook.get().uri(uri).retrieve().body(String.class);
        return body == null ? Map.of() : json.readValue(body, Map.class);
    }

    private NimbusJwtDecoder buildGoogleDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
        OAuth2TokenValidator<Jwt> issuer = jwt -> GOOGLE_ISSUERS.contains(String.valueOf(jwt.getClaims().get("iss")))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Sai issuer", null));
        // Token phải được cấp cho client ID của mình
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(googleClientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Sai audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), issuer, audience));
        return decoder;
    }

    private String appSecretProof(String accessToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(facebookAppSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(accessToken.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseStatusException invalid(String provider) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Đăng nhập " + provider + " không hợp lệ, thử lại nhé");
    }

    private static ResponseStatusException notConfigured(String provider) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Chưa bật đăng nhập bằng " + provider);
    }

    public record SocialProfile(Provider provider, String id, String email, String name) {}
}
