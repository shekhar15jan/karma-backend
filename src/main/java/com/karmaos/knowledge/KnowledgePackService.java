package com.karmaos.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
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

    public List<KnowledgePackResponse> listByWorkspace(UUID workspaceId) {
        return repository.findByWorkspaceIdAndStatusNot(workspaceId, "ARCHIVED")
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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
