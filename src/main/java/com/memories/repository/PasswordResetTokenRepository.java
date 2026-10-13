package com.memories.repository;

import com.memories.entity.PasswordResetToken;
import com.memories.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    boolean existsByUserAndCreatedAtAfter(User user, Instant after);

    @Modifying
    @Query("delete from PasswordResetToken t where t.user = :user")
    int deleteByUser(User user);

    @Modifying
    @Transactional
    @Query("delete from PasswordResetToken t where t.expiresAt < :now")
    int deleteExpired(Instant now);
}
