package com.karmaos.publication;

import com.karmaos.access.OwnershipService;
import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PublicationService {

    private final PublicationRepository publicationRepository;
    private final OwnershipService ownershipService;

    public PublicationService(PublicationRepository publicationRepository,
                              OwnershipService ownershipService) {
        this.publicationRepository = publicationRepository;
        this.ownershipService = ownershipService;
    }

    public List<PublicationResponse> listByArtifact(UUID artifactId, UserPrincipal user) {
        ownershipService.getOwnedArtifact(user.getId(), artifactId);
        return publicationRepository.findByArtifactIdOrderByCreatedAtDesc(artifactId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PublicationResponse create(PublicationRequest request, UserPrincipal user) {
        ownershipService.getOwnedArtifact(user.getId(), request.getArtifactId());
        Publication pub = new Publication();
        pub.setArtifactId(request.getArtifactId());
        pub.setDestination(request.getDestination());
        pub.setStatus("PENDING");
        pub = publicationRepository.save(pub);
        return toResponse(pub);
    }

    @Transactional
    public PublicationResponse publish(UUID id, UserPrincipal user) {
        Publication pub = publicationRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Publication not found: " + id));
        ownershipService.getOwnedArtifact(user.getId(), pub.getArtifactId());
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
