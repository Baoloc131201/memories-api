package com.memories.dto;

import com.memories.entity.Memory;

import java.time.Instant;
import java.time.LocalDate;

public record MemoryResponse(Long id, String title, String description, LocalDate memoryDate,
                             String category, String imageUrl, Instant createdAt) {
    public static MemoryResponse from(Memory m) {
        return new MemoryResponse(m.getId(), m.getTitle(), m.getDescription(), m.getMemoryDate(),
                m.getCategory() != null ? m.getCategory().getName() : null,
                "/api/memories/" + m.getId() + "/image", m.getCreatedAt());
    }
}
