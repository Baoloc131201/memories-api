package com.memories.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Nơi lưu file ảnh, chọn bằng STORAGE_TYPE: local (ổ đĩa, dùng ở máy), db (bảng memory_images trong
 * PostgreSQL, dùng khi deploy gói free) hoặc r2 (Cloudflare R2).
 */
public interface ImageStorage {

    void save(String key, InputStream in, long size, String contentType) throws IOException;

    /** Trả về rỗng nếu file không tồn tại. */
    Optional<StoredImage> load(String key) throws IOException;

    void delete(String key) throws IOException;

    boolean exists(String key) throws IOException;

    /** size = -1 nếu không biết trước dung lượng. */
    record StoredImage(Resource resource, long size) {}
}
