package com.memories.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Chạy một lần ở máy để chuyển ảnh cũ trong ./uploads sang nơi lưu mới:
 * STORAGE_TYPE=db (hoặc r2) + MIGRATE_LOCAL_UPLOADS=true. Ảnh đã có ở nơi lưu mới thì bỏ qua.
 */
@Component
@ConditionalOnProperty(name = "app.storage.migrate-local", havingValue = "true")
public class LocalUploadsMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalUploadsMigration.class);

    private final ImageStorage storage;
    private final Path dir;

    public LocalUploadsMigration(ImageStorage storage, @Value("${app.upload-dir}") String uploadDir) {
        this.storage = storage;
        this.dir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (storage instanceof LocalImageStorage) {
            log.warn("MIGRATE_LOCAL_UPLOADS chỉ dùng khi STORAGE_TYPE=db hoặc r2, bỏ qua");
            return;
        }
        if (!Files.isDirectory(dir)) {
            log.info("Không có thư mục {}, bỏ qua chuyển ảnh", dir);
            return;
        }
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(Files::isRegularFile).toList();
        }
        int uploaded = 0;
        for (Path f : files) {
            String key = f.getFileName().toString();
            if (storage.exists(key)) continue;
            try (InputStream in = Files.newInputStream(f)) {
                storage.save(key, in, Files.size(f), contentType(key));
            }
            uploaded++;
        }
        log.info("Đã chuyển {} / {} ảnh từ {} sang nơi lưu mới", uploaded, files.size(), dir);
    }

    private static String contentType(String key) {
        String k = key.toLowerCase();
        if (k.endsWith(".png")) return "image/png";
        if (k.endsWith(".gif")) return "image/gif";
        if (k.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
