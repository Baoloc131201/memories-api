package com.memories.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

/**
 * Gửi email. Chọn cách gửi theo cấu hình:
 * - BREVO_API_KEY: gọi HTTP API của Brevo (dùng khi deploy, vì Render free chặn cổng SMTP).
 * - MAIL_HOST: gửi qua SMTP (vd. Gmail + App Password), tiện khi chạy ở máy.
 * - Không cấu hình gì: chỉ ghi nội dung ra log để test.
 * Gửi bất đồng bộ để API trả về ngay và không lộ (qua thời gian phản hồi) email có tồn tại hay không.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final String SENDER_NAME = "Kỉ Niệm";

    private final String brevoApiKey;
    private final String smtpHost;
    private final String from;
    private final ObjectProvider<JavaMailSender> smtp;
    private final RestClient brevo = RestClient.create("https://api.brevo.com/v3");

    public MailService(@Value("${app.mail.brevo-api-key}") String brevoApiKey,
                       @Value("${spring.mail.host:}") String smtpHost,
                       @Value("${app.mail.from}") String from,
                       @Value("${spring.mail.username:}") String smtpUsername,
                       ObjectProvider<JavaMailSender> smtp) {
        this.brevoApiKey = brevoApiKey;
        this.smtpHost = smtpHost;
        this.from = from.isBlank() ? smtpUsername : from;
        this.smtp = smtp;
    }

    @Async
    public void sendPasswordReset(String to, String displayName, String link, int expiresInMinutes) {
        String name = HtmlUtils.htmlEscape(displayName);
        String url = HtmlUtils.htmlEscape(link);
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#2d2420">
                  <h2 style="color:#ff7a59">Đặt lại mật khẩu</h2>
                  <p>Chào %s,</p>
                  <p>Bạn (hoặc ai đó) vừa yêu cầu đặt lại mật khẩu cho tài khoản Kỉ Niệm của bạn.
                     Bấm nút bên dưới để đặt mật khẩu mới. Link có hiệu lực trong %d phút và chỉ dùng được một lần.</p>
                  <p style="text-align:center;margin:28px 0">
                    <a href="%s" style="background:#ff7a59;color:#fff;padding:12px 24px;border-radius:999px;
                       text-decoration:none;font-weight:bold">Đặt lại mật khẩu</a>
                  </p>
                  <p style="font-size:13px;color:#8a7b72">Nếu nút không bấm được, mở link này: <br><a href="%s">%s</a></p>
                  <p style="font-size:13px;color:#8a7b72">Nếu bạn không yêu cầu, cứ bỏ qua email này, mật khẩu sẽ không đổi.</p>
                </div>
                """.formatted(name, expiresInMinutes, url, url, url);
        send(to, "Đặt lại mật khẩu Kỉ Niệm", html);
    }

    private void send(String to, String subject, String html) {
        try {
            if (!brevoApiKey.isBlank()) {
                brevo.post().uri("/smtp/email")
                        .header("api-key", brevoApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "sender", Map.of("name", SENDER_NAME, "email", from),
                                "to", List.of(Map.of("email", to)),
                                "subject", subject,
                                "htmlContent", html))
                        .retrieve()
                        .toBodilessEntity();
            } else if (!smtpHost.isBlank()) {
                JavaMailSender sender = smtp.getObject();
                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
                helper.setFrom(from, SENDER_NAME);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(html, true);
                sender.send(message);
            } else {
                log.warn("Chưa cấu hình gửi mail (BREVO_API_KEY hoặc MAIL_HOST). Email gửi tới {}:\n{}", to, html);
                return;
            }
            log.info("Đã gửi email \"{}\" tới {}", subject, to);
        } catch (RestClientException | MailException | MessagingException | UnsupportedEncodingException e) {
            log.error("Gửi email tới {} thất bại: {}", to, e.getMessage());
        }
    }
}
