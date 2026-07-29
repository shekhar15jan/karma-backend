package com.karmaos.report;

import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(ApiConstants.REPORTS)
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.dashboard()));
    }

    @GetMapping("/missions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> missions() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.missions()));
    }
}
