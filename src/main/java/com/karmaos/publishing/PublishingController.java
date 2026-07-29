package com.karmaos.publishing;

import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(ApiConstants.API_V1 + "/publishing")
public class PublishingController {

    @GetMapping("/channels")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> channels() {
        var channels = List.of(
                Map.<String, Object>of("id", "yt-1", "platform", "youtube", "name", "My Channel", "handle", "@mychannel", "status", "connected"),
                Map.<String, Object>of("id", "tt-1", "platform", "tiktok", "name", "TikTok Page", "handle", "@mytiktok", "status", "disconnected")
        );
        return ResponseEntity.ok(ApiResponse.ok(channels));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> logs() {
        var logs = List.of(
                Map.<String, Object>of("id", "p1", "title", "Video #1", "platform", "youtube", "status", "completed", "publishedAt", LocalDateTime.now().toString(), "views", 1200),
                Map.<String, Object>of("id", "p2", "title", "Video #2", "platform", "youtube", "status", "scheduled", "publishedAt", null, "views", 0)
        );
        return ResponseEntity.ok(ApiResponse.ok(logs));
    }
}
