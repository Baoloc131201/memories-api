package com.memories.repository;

import com.memories.entity.SocialAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    @EntityGraph(attributePaths = "user")
    Optional<SocialAccount> findByProviderAndProviderUserId(SocialAccount.Provider provider, String providerUserId);
}
