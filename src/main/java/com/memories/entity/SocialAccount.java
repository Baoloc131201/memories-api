package com.memories.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/** Liên kết giữa một tài khoản Google/Facebook và user trong hệ thống. */
@Entity
@Table(name = "social_accounts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_user_id"}))
public class SocialAccount {

    public enum Provider { GOOGLE, FACEBOOK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    /** ID của user bên Google (`sub`) hoặc Facebook (`id`), không đổi kể cả khi user đổi email. */
    @Column(name = "provider_user_id", nullable = false, length = 100)
    private String providerUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    private String email;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected SocialAccount() {}

    public SocialAccount(Provider provider, String providerUserId, User user, String email) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.user = user;
        this.email = email;
    }

    public Long getId() { return id; }
    public Provider getProvider() { return provider; }
    public String getProviderUserId() { return providerUserId; }
    public User getUser() { return user; }
    public String getEmail() { return email; }
    public Instant getCreatedAt() { return createdAt; }
}
