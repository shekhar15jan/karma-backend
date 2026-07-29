package com.karmaos.prompt;

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
public class PromptService {

    private final PromptRepository promptRepository;
    private final ObjectMapper objectMapper;

    public PromptService(PromptRepository promptRepository, ObjectMapper objectMapper) {
        this.promptRepository = promptRepository;
        this.objectMapper = objectMapper;
    }

    public List<PromptResponse> list() {
        return promptRepository.findByStatus("DRAFT")
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public PromptResponse getById(UUID id) {
        Prompt prompt = promptRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Prompt not found: " + id));
        return toResponse(prompt);
    }

    @Transactional
    public PromptResponse create(PromptRequest request) {
        Prompt prompt = new Prompt();
        prompt.setName(request.getName());
        prompt.setDescription(request.getDescription());
        prompt.setCategory(request.getCategory());
        prompt.setContent(request.getContent());
        prompt.setStatus("DRAFT");
        prompt.setCurrentVersion(1);
        try {
            prompt.setVariables(objectMapper.writeValueAsString(
                    request.getVariables() != null ? request.getVariables() : Collections.emptyList()));
            prompt.setTags(objectMapper.writeValueAsString(
                    request.getTags() != null ? request.getTags() : Collections.emptyList()));
        } catch (Exception e) {
            prompt.setVariables("[]");
            prompt.setTags("[]");
        }
        prompt = promptRepository.save(prompt);
        return toResponse(prompt);
    }

    @Transactional
    public PromptResponse update(UUID id, PromptRequest request) {
        Prompt prompt = promptRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Prompt not found: " + id));
        prompt.setName(request.getName());
        prompt.setDescription(request.getDescription());
        prompt.setCategory(request.getCategory());
        prompt.setContent(request.getContent());
        try {
            prompt.setVariables(objectMapper.writeValueAsString(
                    request.getVariables() != null ? request.getVariables() : Collections.emptyList()));
            prompt.setTags(objectMapper.writeValueAsString(
                    request.getTags() != null ? request.getTags() : Collections.emptyList()));
        } catch (Exception e) {
            prompt.setVariables("[]");
            prompt.setTags("[]");
        }
        prompt = promptRepository.save(prompt);
        return toResponse(prompt);
    }

    @Transactional
    public void delete(UUID id) {
        Prompt prompt = promptRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Prompt not found: " + id));
        prompt.softDelete();
        promptRepository.save(prompt);
    }

    private PromptResponse toResponse(Prompt p) {
        List<String> variables = Collections.emptyList();
        List<String> tags = Collections.emptyList();
        try {
            variables = objectMapper.readValue(p.getVariables(), new TypeReference<List<String>>() {});
            tags = objectMapper.readValue(p.getTags(), new TypeReference<List<String>>() {});
        } catch (Exception ignored) {}

        return PromptResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .category(p.getCategory())
                .content(p.getContent())
                .variables(variables)
                .status(p.getStatus())
                .currentVersion(p.getCurrentVersion())
                .tags(tags)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
