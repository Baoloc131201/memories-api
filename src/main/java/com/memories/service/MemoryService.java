package com.memories.service;

import com.memories.dto.MemoryResponse;
import com.memories.entity.Category;
import com.memories.entity.Memory;
import com.memories.entity.User;
import com.memories.repository.CategoryRepository;
import com.memories.repository.MemoryRepository;
import com.memories.storage.ImageStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp");

    private final MemoryRepository memories;
    private final CategoryRepository categories;
    private final ImageStorage storage;

    public MemoryService(MemoryRepository memories, CategoryRepository categories, ImageStorage storage) {
        this.memories = memories;
        this.categories = categories;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<MemoryResponse> list(User owner) {
        return memories.findByOwnerIdOrderByMemoryDateDescCreatedAtDesc(owner.getId())
                .stream().map(MemoryResponse::from).toList();
    }

    @Transactional
    public MemoryResponse create(User owner, MultipartFile file, String title, String description, String category,
                                 LocalDate date) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ảnh");
        }
        String ext = ALLOWED_TYPES.get(file.getContentType());
        if (ext == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ hỗ trợ ảnh JPG, PNG, GIF, WEBP");
        }
        String t = requireTitle(title);
        Category c = resolveCategory(owner, category);
        String fileName = UUID.randomUUID() + ext;
        try (InputStream in = file.getInputStream()) {
            storage.save(fileName, in, file.getSize(), file.getContentType());
        } catch (Exception e) {
            log.error("Không lưu được ảnh {}", fileName, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không lưu được ảnh");
        }

        Memory m = new Memory();
        m.setOwner(owner);
        m.setTitle(t);
        m.setDescription(trimToNull(description));
        m.setCategory(c);
        m.setMemoryDate(date != null ? date : LocalDate.now());
        m.setFileName(fileName);
        m.setContentType(file.getContentType());
        return MemoryResponse.from(memories.save(m));
    }

    @Transactional
    public MemoryResponse update(User owner, Long id, String title, String description, String category,
                                 LocalDate date) {
        Memory m = find(owner, id);
        Category old = m.getCategory();
        m.setTitle(requireTitle(title));
        m.setDescription(trimToNull(description));
        m.setCategory(resolveCategory(owner, category));
        if (date != null) m.setMemoryDate(date);
        deleteIfUnused(old);
        return MemoryResponse.from(m);
    }

    @Transactional
    public void delete(User owner, Long id) {
        Memory m = find(owner, id);
        memories.delete(m);
        deleteIfUnused(m.getCategory());
        try {
            storage.delete(m.getFileName());
        } catch (Exception e) {
            // file mồ côi không ảnh hưởng dữ liệu
            log.warn("Không xoá được ảnh {}", m.getFileName(), e);
        }
    }

    @Transactional(readOnly = true)
    public ImageFile loadImage(User owner, Long id) {
        Memory m = find(owner, id);
        ImageStorage.StoredImage img;
        try {
            img = storage.load(m.getFileName())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh"));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh");
        }
        return new ImageFile(img.resource(), m.getContentType(), img.size());
    }

    private Memory find(User owner, Long id) {
        return memories.findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy kỉ niệm"));
    }

    /** Tìm phân loại theo tên (không phân biệt hoa thường), chưa có thì tạo mới. Tên trống = chưa phân loại. */
    private Category resolveCategory(User owner, String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim().replaceAll("\\s+", " ");
        if (n.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên phân loại tối đa 50 ký tự");
        }
        return categories.findByOwnerIdAndNameIgnoreCase(owner.getId(), n)
                .orElseGet(() -> categories.save(new Category(owner, n)));
    }

    /** Phân loại không còn ảnh nào thì xoá, để danh sách phân loại luôn khớp với ảnh. */
    private void deleteIfUnused(Category c) {
        if (c != null && !memories.existsByCategoryId(c.getId())) categories.delete(c);
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tiêu đề không được để trống");
        }
        String t = title.trim();
        return t.length() > 120 ? t.substring(0, 120) : t;
    }

    private static String trimToNull(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > 2000 ? t.substring(0, 2000) : t;
    }

    public record ImageFile(Resource resource, String contentType, long size) {}
}
