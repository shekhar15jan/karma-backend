package com.karmaos.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import com.karmaos.junction.AgentSkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SkillService {

    private final SkillRepository skillRepository;
    private final AgentSkillRepository agentSkillRepository;
    private final ObjectMapper objectMapper;

    public SkillService(SkillRepository skillRepository, AgentSkillRepository agentSkillRepository,
                        ObjectMapper objectMapper) {
        this.skillRepository = skillRepository;
        this.agentSkillRepository = agentSkillRepository;
        this.objectMapper = objectMapper;
    }

    public List<SkillResponse> list() {
        return skillRepository.findByStatusNot("ARCHIVED")
                .stream()
                .filter(s -> s.getDeletedAt() == null)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public SkillResponse getById(UUID id) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Skill not found: " + id));
        return toResponse(skill);
    }

    @Transactional
    public SkillResponse create(SkillRequest request) {
        Skill skill = new Skill();
        skill.setName(request.getName());
        skill.setDescription(request.getDescription());
        skill.setContent(request.getContent());
        skill.setCategory(request.getCategory());
        skill.setStatus("ACTIVE");
        skill = skillRepository.save(skill);
        return toResponse(skill);
    }

    @Transactional
    public SkillResponse update(UUID id, SkillRequest request) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Skill not found: " + id));
        skill.setName(request.getName());
        skill.setDescription(request.getDescription());
        skill.setContent(request.getContent());
        skill.setCategory(request.getCategory());
        skill.setValidationRules(toJson(request.getValidationRules()));
        skill.setExecutionParameters(toJson(request.getExecutionParameters()));
        skill = skillRepository.save(skill);
        return toResponse(skill);
    }

    private String toJson(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    @Transactional
    public void delete(UUID id) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Skill not found: " + id));
        if (skill.isSystem()) {
            throw ErrorCode.BAD_REQUEST.exception("System skill cannot be deleted");
        }
        skill.softDelete();
        skill.setStatus("ARCHIVED");
        skillRepository.save(skill);
        agentSkillRepository.deleteAllByIdSkillId(id);
    }

    @Transactional
    public void activate(UUID id) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Skill not found: " + id));
        if (skill.getDeletedAt() != null) {
            throw ErrorCode.NOT_FOUND.exception("Skill not found: " + id);
        }
        skill.setStatus("ACTIVE");
        skill.setDeletedAt(null);
        skillRepository.save(skill);
    }

    @Transactional
    public void deactivate(UUID id) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Skill not found: " + id));
        if (skill.getDeletedAt() != null) {
            throw ErrorCode.NOT_FOUND.exception("Skill not found: " + id);
        }
        skill.setStatus("DRAFT");
        skillRepository.save(skill);
    }

    private SkillResponse toResponse(Skill s) {
        int assignedAgentCount = agentSkillRepository.findByIdSkillId(s.getId()).size();
        return SkillResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .description(s.getDescription())
                .content(s.getContent())
                .validationRules(parseJson(s.getValidationRules()))
                .executionParameters(parseJson(s.getExecutionParameters()))
                .category(s.getCategory())
                .status(s.getStatus())
                .assignedAgentCount(assignedAgentCount)
                .isSystem(s.isSystem())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private Object parseJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            return value;
        }
    }
}
