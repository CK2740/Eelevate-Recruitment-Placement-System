package com.recruitment.controller;

import com.recruitment.dto.ReportResponse;
import com.recruitment.dto.ReportFilters;
import com.recruitment.dto.ReportType;
import com.recruitment.service.ReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/{type}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ReportResponse getReport(
            @PathVariable ReportType type,
            @ModelAttribute ReportFilters filters,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "100") final int size) {
        return reportService.getReport(type, filters, page, size);
    }

    @GetMapping("/{type}/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<StreamingResponseBody> exportCsv(
            @PathVariable ReportType type,
            @ModelAttribute ReportFilters filters) {
        StreamingResponseBody csv = reportService.exportCsv(type, filters);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header("Content-Disposition", ContentDisposition.attachment()
                        .filename(type.name().toLowerCase() + ".csv")
                        .build()
                        .toString())
                .body(csv);
    }
}
