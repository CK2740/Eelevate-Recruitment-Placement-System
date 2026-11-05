package com.recruitment.service;

import com.recruitment.dao.PlacementDao;
import com.recruitment.dto.PlacementPageResponse;
import com.recruitment.dto.PlacementRequest;
import com.recruitment.dto.PlacementResponse;
import com.recruitment.dto.PlacementStatus;
import com.recruitment.dto.PlacementStatusHistoryResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlacementService {

    private final PlacementDao placementDao;

    public PlacementService(PlacementDao placementDao) {
        this.placementDao = placementDao;
    }

    @Transactional
    public PlacementResponse create(
            long applicationId,
            PlacementRequest request,
            long userId) {
        PlacementDao.PlacementContext context = lockApplication(applicationId);
        if (!"ACCEPTED".equals(context.offerStatus())) {
            throw conflict("A placement can be created only after the candidate accepts an offer");
        }
        if (!"OFFERED".equals(context.applicationStatus())) {
            throw conflict("A placement can be created only for an offered application");
        }
        if (placementDao.placementExists(applicationId)) {
            throw conflict("A placement already exists for this application");
        }
        if (request.joiningStatus() == PlacementStatus.JOINED
                && request.actualJoiningDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Actual joining date is required when the candidate has joined");
        }
        if (request.joiningStatus() == PlacementStatus.JOINED
                && request.actualJoiningDate().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Actual joining date cannot be in the future");
        }
        try {
            long placementId = placementDao.insert(applicationId, request, userId);
            return placementDao.findById(placementId);
        } catch (DuplicateKeyException exception) {
            throw conflict("A placement already exists for this application");
        }
    }

    @Transactional
    public PlacementResponse update(
            long placementId,
            PlacementRequest request,
            String reason,
            long userId) {
        PlacementDao.PlacementContext context = lockPlacement(placementId);
        if (!isValidTransition(context.joiningStatus(), request.joiningStatus())) {
            throw conflict("Invalid placement status transition from " + context.joiningStatus()
                    + " to " + request.joiningStatus());
        }
        if (request.joiningStatus() == PlacementStatus.JOINED
                && request.actualJoiningDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Actual joining date is required when the candidate has joined");
        }
        if (request.joiningStatus() == PlacementStatus.JOINED
                && request.actualJoiningDate().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Actual joining date cannot be in the future");
        }
        if (!hasText(reason)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A reason is required when changing placement status");
        }
        if (!placementDao.update(
                placementId, context.joiningStatus(), request, userId, reason)) {
            throw conflict("Placement status changed before the request could be completed");
        }
        return placementDao.findById(placementId);
    }

    @Transactional(readOnly = true)
    public PlacementResponse getById(long placementId) {
        return findByIdOrNotFound(placementId);
    }

    @Transactional(readOnly = true)
    public PlacementPageResponse getPlacements(
            String search,
            String candidate,
            String company,
            String designation,
            PlacementStatus status,
            Long recruiterId,
            int page,
            int size) {
        return placementDao.findAll(
                search, candidate, company, designation, status, recruiterId, page, size);
    }

    @Transactional(readOnly = true)
    public List<PlacementStatusHistoryResponse> getHistory(long placementId) {
        findByIdOrNotFound(placementId);
        return placementDao.findHistory(placementId);
    }

    private PlacementDao.PlacementContext lockApplication(long applicationId) {
        try {
            return placementDao.lockApplication(applicationId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Application " + applicationId + " was not found");
        }
    }

    private PlacementDao.PlacementContext lockPlacement(long placementId) {
        try {
            return placementDao.lockPlacement(placementId);
        } catch (EmptyResultDataAccessException exception) {
            throw placementNotFound(placementId);
        }
    }

    private PlacementResponse findByIdOrNotFound(long placementId) {
        try {
            return placementDao.findById(placementId);
        } catch (EmptyResultDataAccessException exception) {
            throw placementNotFound(placementId);
        }
    }

    private static boolean isValidTransition(PlacementStatus current, PlacementStatus next) {
        return current == PlacementStatus.PENDING
                && (next == PlacementStatus.JOINED || next == PlacementStatus.NOT_JOINED);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException placementNotFound(long placementId) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Placement " + placementId + " was not found");
    }
}
