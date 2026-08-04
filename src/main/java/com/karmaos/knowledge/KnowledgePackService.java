package com.karmaos.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.access.OwnershipService;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import com.karmaos.knowledgedocument.KnowledgeDocument;
import com.karmaos.knowledgedocument.KnowledgeDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KnowledgePackService {

    private static final long MAX_FILE_BYTES = 25L * 1024 * 1024;

    private final KnowledgePackRepository repository;
    private final KnowledgeDocumentRepository documentRepository;
    private final OwnershipService ownershipService;
    private final ObjectMapper objectMapper;
    private final EmbeddingService embeddingService;

    public KnowledgePackService(KnowledgePackRepository repository,
                                KnowledgeDocumentRepository documentRepository,
                                OwnershipService ownershipService,
                                ObjectMapper objectMapper,
                                EmbeddingService embeddingService) {
        this.repository = repository;
        this.documentRepository = documentRepository;
        this.ownershipService = ownershipService;
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
    }

    public List<KnowledgePackResponse> listAll(UUID userId) {
        List<UUID> workspaceIds = ownershipService.getOwnedWorkspaceIds(userId);
        if (workspaceIds.isEmpty()) {
            return List.of();
        }
        return repository.findByWorkspaceIdInAndStatusNot(workspaceIds, "ARCHIVED").stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<KnowledgePackResponse> listByWorkspace(UUID workspaceId, UUID userId) {
        ownershipService.getOwnedWorkspace(userId, workspaceId);
        return repository.findByWorkspaceIdAndStatusNot(workspaceId, "ARCHIVED")
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> uploadFile(MultipartFile file, UUID workspaceId, UUID userId) {
        if (file == null || file.isEmpty()) {
            return Map.of("success", false, "message", "No file provided");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            return Map.of("success", false, "message", "File exceeds the 25MB limit");
        }

        UUID targetWorkspace = workspaceId;
        if (targetWorkspace == null) {
            List<UUID> owned = ownershipService.getOwnedWorkspaceIds(userId);
            if (owned.isEmpty()) {
                return Map.of("success", false, "message", "No workspace to upload into; create a workspace first");
            }
            targetWorkspace = owned.get(0);
        } else {
            ownershipService.getOwnedWorkspace(userId, targetWorkspace);
        }

        try {
            String uploadDir = System.getProperty("java.io.tmpdir") + "/knowledge/";
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            byte[] contentBytes = Files.readAllBytes(filePath);
            String content = new String(contentBytes, StandardCharsets.UTF_8);
            String checksum = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(contentBytes));

            KnowledgePack pack = new KnowledgePack();
            pack.setName(file.getOriginalFilename());
            pack.setDescription("Uploaded file: " + file.getOriginalFilename());
            pack.setCategory("file");
            pack.setStatus("INDEXED");
            pack.setWorkspaceId(targetWorkspace);
            pack = repository.save(pack);

            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setKnowledgePackId(pack.getId());
            doc.setFilename(file.getOriginalFilename());
            doc.setContent(content);
            doc.setSize(file.getSize());
            doc.setIndexed(true);
            doc.setChecksum(checksum);
            documentRepository.save(doc);
            embeddingService.indexPack(pack.getId());

            return Map.of(
                    "success", true,
                    "id", pack.getId().toString(),
                    "name", pack.getName(),
                    "status", pack.getStatus()
            );
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            return Map.of("success", false, "message", "Upload failed: " + e.getMessage());
        }
    }

    public KnowledgePackResponse getById(UUID id, UUID userId) {
        KnowledgePack pack = getOwnedPack(id, userId);
        return toResponse(pack);
    }

    public Map<String, Object> reindex(UUID id, UUID userId) {
        getOwnedPack(id, userId);
        embeddingService.indexPack(id);
        return Map.of("success", true, "status", "INDEXED");
    }

    @Transactional
    public KnowledgePackResponse create(KnowledgePackRequest request, UUID userId) {
        List<UUID> owned = ownershipService.getOwnedWorkspaceIds(userId);
        if (owned.isEmpty()) {
            throw ErrorCode.BAD_REQUEST.exception("No workspace to create a pack in; create a workspace first");
        }
        UUID targetWorkspace = owned.get(0);

        KnowledgePack pack = new KnowledgePack();
        pack.setName(request.getName());
        pack.setDescription(request.getDescription());
        pack.setCategory(request.getCategory());
        pack.setWorkspaceId(targetWorkspace);
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
    public KnowledgePackResponse update(UUID id, KnowledgePackRequest request, UUID userId) {
        KnowledgePack pack = getOwnedPack(id, userId);
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
    public void delete(UUID id, UUID userId) {
        KnowledgePack pack = getOwnedPack(id, userId);
        pack.softDelete();
        repository.save(pack);
    }

    private KnowledgePack getOwnedPack(UUID id, UUID userId) {
        KnowledgePack pack = repository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Knowledge pack not found: " + id));
        if (pack.getWorkspaceId() != null) {
            ownershipService.getOwnedWorkspace(userId, pack.getWorkspaceId());
        }
        return pack;
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
                .documentCount((int) documentRepository.countByKnowledgePackId(p.getId()))
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
