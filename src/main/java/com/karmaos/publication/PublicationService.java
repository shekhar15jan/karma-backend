package com.karmaos.publication;

import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PublicationService {

    private final PublicationRepository publicationRepository;

    public PublicationService(PublicationRepository publicationRepository) {
        this.publicationRepository = publicationRepository;
    }

    public List<PublicationResponse> listByArtifact(Long artifactId) {
        return publicationRepository.findByArtifactIdOrderByCreatedAtDesc(artifactId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PublicationResponse create(PublicationRequest request) {
        Publication pub = new Publication();
        pub.setArtifactId(request.getArtifactId());
        pub.setDestination(request.getDestination());
        pub.setStatus("PENDING");
        pub = publicationRepository.save(pub);
        return toResponse(pub);
    }

    @Transactional
    public PublicationResponse publish(Long id) {
        Publication pub = publicationRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Publication not found: " + id));
        pub.setStatus("PUBLISHED");
        pub.setPublishedAt(LocalDateTime.now());
        pub = publicationRepository.save(pub);
        return toResponse(pub);
    }

    private PublicationResponse toResponse(Publication p) {
        return PublicationResponse.builder()
                .id(p.getId())
                .artifactId(p.getArtifactId())
                .destination(p.getDestination())
                .status(p.getStatus())
                .publishedAt(p.getPublishedAt())
                .url(p.getUrl())
                .errorMessage(p.getErrorMessage())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
