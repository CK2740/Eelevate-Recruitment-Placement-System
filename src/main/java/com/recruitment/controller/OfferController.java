package com.recruitment.controller;

import com.recruitment.dto.OfferJoiningDateRequest;
import com.recruitment.dto.OfferPageResponse;
import com.recruitment.dto.OfferRequest;
import com.recruitment.dto.OfferResponse;
import com.recruitment.dto.OfferStatus;
import com.recruitment.dto.OfferStatusHistoryResponse;
import com.recruitment.dto.OfferStatusRequest;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.OfferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @PostMapping("/applications/{applicationId}/offers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public OfferResponse create(
            @PathVariable @Min(1) long applicationId,
            @Valid @RequestBody OfferRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return offerService.create(
                applicationId,
                request,
                principal.getUserId(),
                hasRole(principal, "ADMIN"),
                false);
    }

    @GetMapping("/offers")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public OfferPageResponse getOffers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String candidate,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String designation,
            @RequestParam(required = false) OfferStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryBefore,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return offerService.getOffers(
                search,
                candidate,
                company,
                designation,
                status,
                expiryBefore,
                principal.getUserId(),
                hasRole(principal, "RECRUITER"),
                page,
                size);
    }

    @GetMapping("/offers/{offerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public OfferResponse getById(
            @PathVariable @Min(1) long offerId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return offerService.getById(
                offerId,
                principal.getUserId(),
                hasRole(principal, "ADMIN"),
                false);
    }

    @PutMapping("/offers/{offerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public OfferResponse update(
            @PathVariable @Min(1) long offerId,
            @Valid @RequestBody OfferRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return offerService.update(
                offerId,
                request,
                principal.getUserId(),
                hasRole(principal, "ADMIN"),
                false);
    }

    @PatchMapping("/offers/{offerId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public OfferResponse updateStatus(
            @PathVariable @Min(1) long offerId,
            @Valid @RequestBody OfferStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return offerService.updateStatus(
                offerId,
                request,
                principal.getUserId(),
                hasRole(principal, "ADMIN"),
                false);
    }

    @PutMapping("/offers/{offerId}/joining-date")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public OfferResponse updateJoiningDate(
            @PathVariable @Min(1) long offerId,
            @Valid @RequestBody OfferJoiningDateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return offerService.updateJoiningDate(
                offerId,
                request,
                principal.getUserId(),
                hasRole(principal, "ADMIN"),
                false);
    }

    @GetMapping("/offers/{offerId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public List<OfferStatusHistoryResponse> getHistory(
            @PathVariable @Min(1) long offerId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return offerService.getHistory(
                offerId,
                principal.getUserId(),
                hasRole(principal, "ADMIN"),
                false);
    }

    private static boolean hasRole(UserPrincipal principal, String role) {
        return principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_" + role));
    }
}
