package com.karmaos.calendar;

import com.karmaos.access.OwnershipService;
import com.karmaos.artifact.Artifact;
import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import com.karmaos.publication.Publication;
import com.karmaos.publication.PublicationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping(ApiConstants.API_V1 + "/calendar")
public class CalendarController {

    private final ArtifactRepository artifactRepository;
    private final PublicationRepository publicationRepository;
    private final OwnershipService ownershipService;

    public CalendarController(ArtifactRepository artifactRepository,
                              PublicationRepository publicationRepository,
                              OwnershipService ownershipService) {
        this.artifactRepository = artifactRepository;
        this.publicationRepository = publicationRepository;
        this.ownershipService = ownershipService;
    }

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> events(
            @AuthenticationPrincipal UserPrincipal user) {
        List<UUID> missionIds = ownershipService.getOwnedMissionIds(user.getId());
        List<Artifact> ownedArtifacts = missionIds.isEmpty()
                ? List.of()
                : artifactRepository.findByMissionIdInOrderByCreatedAtDesc(missionIds);
        Map<UUID, Artifact> artifactById = ownedArtifacts.stream()
                .collect(Collectors.toMap(Artifact::getId, a -> a, (a, b) -> a));

        List<Publication> publications = artifactById.isEmpty()
                ? List.of()
                : publicationRepository.findByArtifactIdInOrderByCreatedAtDesc(artifactById.keySet());

        var events = publications.stream()
                .<Map<String, Object>>map(p -> {
                    Artifact artifact = artifactById.get(p.getArtifactId());
                    var event = new HashMap<String, Object>();
                    event.put("id", p.getId().toString());
                    event.put("title", artifact != null && artifact.getName() != null ? artifact.getName() : "Publication");
                    event.put("platform", p.getDestination() != null ? p.getDestination().toLowerCase() : "webhook");
                    event.put("status", toStatusLabel(p.getStatus()));
                    event.put("url", p.getUrl());
                    java.time.LocalDateTime at = p.getPublishedAt() != null ? p.getPublishedAt() : p.getCreatedAt();
                    event.put("day", at != null ? getDayOfWeek(at.getDayOfWeek().name()) : "Monday");
                    event.put("time", at != null ? at.toLocalTime().toString().substring(0, 5) : "10:00");
                    return event;
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(events));
    }

    private String toStatusLabel(String status) {
        return switch (status == null ? "" : status.toUpperCase()) {
            case "COMPLETED", "PUBLISHED" -> "completed";
            case "FAILED" -> "failed";
            default -> "pending";
        };
    }

    private String getDayOfWeek(String day) {
        return switch (day.toUpperCase()) {
            case "MONDAY" -> "Monday";
            case "TUESDAY" -> "Tuesday";
            case "WEDNESDAY" -> "Wednesday";
            case "THURSDAY" -> "Thursday";
            case "FRIDAY" -> "Friday";
            case "SATURDAY" -> "Saturday";
            case "SUNDAY" -> "Sunday";
            default -> "Monday";
        };
    }
}
