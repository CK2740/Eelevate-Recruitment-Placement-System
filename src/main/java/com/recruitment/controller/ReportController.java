package com.recruitment.controller;

import com.recruitment.dto.ReportResponse;
import com.recruitment.dto.ReportType;
import com.recruitment.service.ReportService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'PLACEMENT_OFFICER')")
    public ReportResponse getReport(
            @PathVariable ReportType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String technology,
            @RequestParam(required = false) Long recruiterId,
            @RequestParam(required = false) Integer passingYear) {
        return reportService.getReport(type, from, to, companyId, technology, recruiterId, passingYear);
    }

    @GetMapping("/{type}/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'PLACEMENT_OFFICER')")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable ReportType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String technology,
            @RequestParam(required = false) Long recruiterId,
            @RequestParam(required = false) Integer passingYear) {
        byte[] csv = reportService.exportCsv(
                type, from, to, companyId, technology, recruiterId, passingYear);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(type.name().toLowerCase() + ".csv")
                .build());
        return ResponseEntity.ok().headers(headers).body(csv);
    }
}
