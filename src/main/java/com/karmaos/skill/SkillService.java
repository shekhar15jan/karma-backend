package com.karmaos.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SkillService {

    private final SkillRepository skillRepository;

    public SkillService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    public List<SkillResponse> list() {
        return skillRepository.findByStatusNot("ARCHIVED")
                .stream()
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
        skill.setCategory(request.getCategory());
        skill.setStatus("DRAFT");
        skill = skillRepository.save(skill);
        return toResponse(skill);
    }

    @Transactional
    public SkillResponse update(UUID id, SkillRequest request) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Skill not found: " + id));
        skill.setName(request.getName());
        skill.setDescription(request.getDescription());
        skill.setCategory(request.getCategory());
        skill = skillRepository.save(skill);
        return toResponse(skill);
    }

    @Transactional
    public void delete(UUID id) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Skill not found: " + id));
        skill.softDelete();
        skillRepository.save(skill);
    }

    @Transactional
    public void install(UUID id) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Skill not found: " + id));
        skill.setStatus("INSTALLED");
        skillRepository.save(skill);
    }

    private SkillResponse toResponse(Skill s) {
        return SkillResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .description(s.getDescription())
                .category(s.getCategory())
                .status(s.getStatus())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
