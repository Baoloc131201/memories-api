package com.memories.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/** Nơi lưu file ảnh. Chạy ở máy dùng ổ đĩa (local), deploy dùng Cloudflare R2 (r2). Chọn bằng STORAGE_TYPE. */
public interface ImageStorage {

    void save(String key, InputStream in, long size, String contentType) throws IOException;

    /** Trả về rỗng nếu file không tồn tại. */
    Optional<StoredImage> load(String key) throws IOException;

    void delete(String key) throws IOException;

    /** size = -1 nếu không biết trước dung lượng. */
    record StoredImage(Resource resource, long size) {}
}
