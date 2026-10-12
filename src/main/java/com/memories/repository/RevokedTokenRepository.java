package com.memories.repository;

import com.memories.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {
    @Modifying
    @Transactional
    @Query("delete from RevokedToken t where t.expiresAt < :now")
    int deleteExpired(Instant now);
}
