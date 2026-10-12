package com.memories.repository;

import com.memories.entity.MemoryImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryImageRepository extends JpaRepository<MemoryImage, String> {
}
