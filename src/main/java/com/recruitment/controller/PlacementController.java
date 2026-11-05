package com.recruitment.controller;

import com.recruitment.dto.PlacementPageResponse;
import com.recruitment.dto.PlacementRequest;
import com.recruitment.dto.PlacementResponse;
import com.recruitment.dto.PlacementStatus;
import com.recruitment.dto.PlacementStatusHistoryResponse;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.PlacementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class PlacementController {

    private final PlacementService placementService;

    public PlacementController(PlacementService placementService) {
        this.placementService = placementService;
    }

    @PostMapping("/applications/{applicationId}/placements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'PLACEMENT_OFFICER')")
    public PlacementResponse create(
            @PathVariable @Min(1) long applicationId,
            @Valid @RequestBody PlacementRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return placementService.create(applicationId, request, principal.getUserId());
    }

    @GetMapping("/placements")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'PLACEMENT_OFFICER')")
    public PlacementPageResponse getPlacements(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String candidate,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String designation,
            @RequestParam(required = false) PlacementStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return placementService.getPlacements(
                search,
                candidate,
                company,
                designation,
                status,
                hasRole(principal, "RECRUITER") ? principal.getUserId() : null,
                page,
                size);
    }

    @GetMapping("/placements/{placementId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLACEMENT_OFFICER')")
    public PlacementResponse getById(@PathVariable @Min(1) long placementId) {
        return placementService.getById(placementId);
    }

    @PutMapping("/placements/{placementId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLACEMENT_OFFICER')")
    public PlacementResponse update(
            @PathVariable @Min(1) long placementId,
            @Valid @RequestBody PlacementRequest request,
            @RequestParam @Size(min = 1, max = 500) String reason,
            @AuthenticationPrincipal UserPrincipal principal) {
        return placementService.update(
                placementId, request, reason, principal.getUserId());
    }

    @GetMapping("/placements/{placementId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLACEMENT_OFFICER')")
    public List<PlacementStatusHistoryResponse> getHistory(
            @PathVariable @Min(1) long placementId) {
        return placementService.getHistory(placementId);
    }

    private static boolean hasRole(UserPrincipal principal, String role) {
        return principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_" + role));
    }
}
