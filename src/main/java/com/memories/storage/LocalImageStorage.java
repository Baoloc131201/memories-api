package com.memories.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalImageStorage implements ImageStorage {

    private final Path dir;

    public LocalImageStorage(@Value("${app.upload-dir}") String uploadDir) throws IOException {
        this.dir = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
    }

    @Override
    public void save(String key, InputStream in, long size, String contentType) throws IOException {
        Files.copy(in, dir.resolve(key), StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Optional<StoredImage> load(String key) throws IOException {
        Path file = dir.resolve(key);
        if (!Files.exists(file)) return Optional.empty();
        return Optional.of(new StoredImage(new UrlResource(file.toUri()), Files.size(file)));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(dir.resolve(key));
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(dir.resolve(key));
    }
}
