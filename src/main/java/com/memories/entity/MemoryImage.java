package com.memories.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** File ảnh lưu thẳng trong DB (STORAGE_TYPE=db). Khoá là tên file UUID, trùng với memories.file_name. */
@Entity
@Table(name = "memory_images")
public class MemoryImage {

    @Id
    @Column(name = "file_key", length = 64)
    private String key;

    @Column(nullable = false, length = 50)
    private String contentType;

    // byte[] được Hibernate map sang bytea của PostgreSQL. Không dùng @Lob vì sẽ thành large object (oid)
    @Column(nullable = false)
    private byte[] data;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected MemoryImage() {}

    public MemoryImage(String key, String contentType, byte[] data) {
        this.key = key;
        this.contentType = contentType;
        this.data = data;
    }

    public String getKey() { return key; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
}
