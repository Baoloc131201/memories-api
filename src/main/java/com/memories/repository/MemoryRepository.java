package com.memories.repository;

import com.memories.entity.Memory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemoryRepository extends JpaRepository<Memory, Long> {
    @EntityGraph(attributePaths = "category")
    List<Memory> findByOwnerIdOrderByMemoryDateDescCreatedAtDesc(Long ownerId);
    Optional<Memory> findByIdAndOwnerId(Long id, Long ownerId);
    boolean existsByCategoryId(Long categoryId);
}
