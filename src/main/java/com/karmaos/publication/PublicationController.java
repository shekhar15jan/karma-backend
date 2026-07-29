package com.karmaos.publication;

import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping(ApiConstants.PUBLICATIONS)
public class PublicationController {

    private final PublicationService publicationService;

    public PublicationController(PublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @GetMapping("/artifact/{artifactId}")
    public ResponseEntity<ApiResponse<List<PublicationResponse>>> list(@PathVariable Long artifactId) {
        return ResponseEntity.ok(ApiResponse.ok(publicationService.listByArtifact(artifactId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PublicationResponse>> create(@Valid @RequestBody PublicationRequest request) {
        return ResponseEntity.ok(ApiResponse.created(publicationService.create(request)));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<PublicationResponse>> publish(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(publicationService.publish(id)));
    }
}
