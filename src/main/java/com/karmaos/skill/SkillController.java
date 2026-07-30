package com.karmaos.skill;

import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.SKILLS)
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SkillResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(skillService.list()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SkillResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(skillService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SkillResponse>> create(@Valid @RequestBody SkillRequest request) {
        return ResponseEntity.ok(ApiResponse.created(skillService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SkillResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody SkillRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(skillService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        skillService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Skill deleted"));
    }

    @PostMapping("/{id}/install")
    public ResponseEntity<ApiResponse<Void>> install(@PathVariable UUID id) {
        skillService.install(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Skill installed"));
    }
}
