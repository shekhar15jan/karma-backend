package com.karmaos.calendar;

import com.karmaos.artifact.Artifact;
import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(ApiConstants.API_V1 + "/calendar")
public class CalendarController {

    private final ArtifactRepository artifactRepository;

    public CalendarController(ArtifactRepository artifactRepository) {
        this.artifactRepository = artifactRepository;
    }

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> events() {
        List<Artifact> published = artifactRepository.findByPublicationStatus("PUBLISHED");
        var events = published.stream()
                .map(a -> Map.<String, Object>of(
                        "id", a.getId().toString(),
                        "title", a.getName(),
                        "platform", "youtube",
                        "status", "completed",
                        "day", getDayOfWeek(a.getUpdatedAt() != null ? a.getUpdatedAt().getDayOfWeek().name() : "MONDAY"),
                        "time", a.getUpdatedAt() != null ? a.getUpdatedAt().toLocalTime().toString().substring(0, 5) : "10:00"
                ))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(events));
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
