package com.karmaos.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KnowledgePackService {

    private final KnowledgePackRepository repository;
    private final ObjectMapper objectMapper;

    public KnowledgePackService(KnowledgePackRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public List<KnowledgePackResponse> listAll() {
        return repository.findAll().stream()
                .filter(p -> !"ARCHIVED".equals(p.getStatus()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<KnowledgePackResponse> listByWorkspace(UUID workspaceId) {
        return repository.findByWorkspaceIdAndStatusNot(workspaceId, "ARCHIVED")
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Map<String, Object> uploadFile(MultipartFile file, UUID workspaceId) {
        try {
            String uploadDir = System.getProperty("java.io.tmpdir") + "/knowledge/";
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            KnowledgePack pack = new KnowledgePack();
            pack.setName(file.getOriginalFilename());
            pack.setDescription("Uploaded file: " + file.getOriginalFilename());
            pack.setCategory("file");
            pack.setStatus("INDEXING");
            pack.setWorkspaceId(workspaceId);
            pack = repository.save(pack);

            return Map.of(
                    "success", true,
                    "id", pack.getId().toString(),
                    "name", pack.getName(),
                    "status", pack.getStatus()
            );
        } catch (IOException e) {
            return Map.of("success", false, "message", "Upload failed: " + e.getMessage());
        }
    }

    public KnowledgePackResponse getById(UUID id) {
        KnowledgePack pack = repository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Knowledge pack not found: " + id));
        return toResponse(pack);
    }

    @Transactional
    public KnowledgePackResponse create(KnowledgePackRequest request) {
        KnowledgePack pack = new KnowledgePack();
        pack.setName(request.getName());
        pack.setDescription(request.getDescription());
        pack.setCategory(request.getCategory());
        pack.setStatus("DRAFT");
        try {
            pack.setTags(objectMapper.writeValueAsString(
                    request.getTags() != null ? request.getTags() : Collections.emptyList()));
        } catch (Exception e) {
            pack.setTags("[]");
        }
        pack = repository.save(pack);
        return toResponse(pack);
    }

    @Transactional
    public KnowledgePackResponse update(UUID id, KnowledgePackRequest request) {
        KnowledgePack pack = repository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Knowledge pack not found: " + id));
        pack.setName(request.getName());
        pack.setDescription(request.getDescription());
        pack.setCategory(request.getCategory());
        try {
            pack.setTags(objectMapper.writeValueAsString(
                    request.getTags() != null ? request.getTags() : Collections.emptyList()));
        } catch (Exception e) {
            pack.setTags("[]");
        }
        pack = repository.save(pack);
        return toResponse(pack);
    }

    @Transactional
    public void delete(UUID id) {
        KnowledgePack pack = repository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Knowledge pack not found: " + id));
        pack.softDelete();
        repository.save(pack);
    }

    private KnowledgePackResponse toResponse(KnowledgePack p) {
        List<String> tags = Collections.emptyList();
        try {
            tags = objectMapper.readValue(p.getTags(), new TypeReference<List<String>>() {});
        } catch (Exception ignored) {}

        return KnowledgePackResponse.builder()
                .id(p.getId())
                .workspaceId(p.getWorkspaceId())
                .name(p.getName())
                .description(p.getDescription())
                .category(p.getCategory())
                .status(p.getStatus())
                .tags(tags)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
