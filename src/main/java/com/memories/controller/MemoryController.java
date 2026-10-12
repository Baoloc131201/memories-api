package com.memories.controller;

import com.memories.dto.MemoryResponse;
import com.memories.entity.User;
import com.memories.service.AuthService;
import com.memories.service.MemoryService;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/memories")
public class MemoryController {

    private final MemoryService memoryService;
    private final AuthService authService;

    public MemoryController(MemoryService memoryService, AuthService authService) {
        this.memoryService = memoryService;
        this.authService = authService;
    }

    @GetMapping
    public List<MemoryResponse> list(@AuthenticationPrincipal UserDetails principal) {
        return memoryService.list(currentUser(principal));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryResponse create(@AuthenticationPrincipal UserDetails principal,
                                 @RequestParam("file") MultipartFile file,
                                 @RequestParam("title") String title,
                                 @RequestParam(value = "description", required = false) String description,
                                 @RequestParam(value = "category", required = false) String category,
                                 @RequestParam(value = "memoryDate", required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate memoryDate) {
        return memoryService.create(currentUser(principal), file, title, description, category, memoryDate);
    }

    @PutMapping("/{id}")
    public MemoryResponse update(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id,
                                 @RequestBody UpdateRequest req) {
        return memoryService.update(currentUser(principal), id, req.title(), req.description(), req.category(),
                req.memoryDate());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        memoryService.delete(currentUser(principal), id);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        MemoryService.ImageFile img = memoryService.loadImage(currentUser(principal), id);
        ResponseEntity.BodyBuilder res = ResponseEntity.ok();
        if (img.size() >= 0) res.contentLength(img.size());
        return res
                .contentType(MediaType.parseMediaType(img.contentType()))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate())
                .body(img.resource());
    }

    private User currentUser(UserDetails principal) {
        return authService.getUser(principal.getUsername());
    }

    public record UpdateRequest(String title, String description, String category, LocalDate memoryDate) {}
}
