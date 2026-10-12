package com.memories.storage;

import com.memories.entity.MemoryImage;
import com.memories.repository.MemoryImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Lưu ảnh trong bảng memory_images của PostgreSQL (Neon). Không cần dịch vụ lưu file riêng,
 * hợp với gói free của Render (ổ đĩa bị xoá mỗi lần khởi động lại). Ghi ảnh chung transaction
 * với kỉ niệm nên lỗi giữa chừng thì không để lại ảnh mồ côi.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "db")
public class DbImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(DbImageStorage.class);

    private final MemoryImageRepository images;

    public DbImageStorage(MemoryImageRepository images) {
        this.images = images;
        log.info("Lưu ảnh trong database (bảng memory_images)");
    }

    @Override
    @Transactional
    public void save(String key, InputStream in, long size, String contentType) throws IOException {
        images.save(new MemoryImage(key, contentType, in.readAllBytes()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredImage> load(String key) {
        return images.findById(key).map(img -> {
            byte[] data = img.getData();
            return new StoredImage(new ByteArrayResource(data), data.length);
        });
    }

    @Override
    @Transactional
    public void delete(String key) {
        images.deleteById(key);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String key) {
        return images.existsById(key);
    }
}
