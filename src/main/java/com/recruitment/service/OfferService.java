package com.recruitment.service;

import com.recruitment.dao.OfferDao;
import com.recruitment.dto.OfferPageResponse;
import com.recruitment.dto.OfferJoiningDateRequest;
import com.recruitment.dto.OfferRequest;
import com.recruitment.dto.OfferResponse;
import com.recruitment.dto.OfferStatus;
import com.recruitment.dto.OfferStatusHistoryResponse;
import com.recruitment.dto.OfferStatusRequest;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OfferService {

    private final OfferDao offerDao;

    public OfferService(OfferDao offerDao) {
        this.offerDao = offerDao;
    }

    @Transactional
    public OfferResponse create(
            long applicationId,
            OfferRequest request,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        OfferDao.OfferContext application = lockApplication(applicationId);
        requireManagementAccess(application.assignedRecruiterId(), userId, administrator, placementOfficer);
        if (!"SELECTED".equals(application.applicationStatus())) {
            throw conflict("Offers can be created only for selected applications");
        }
        if (request.expiryDate().isBefore(request.offerDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Offer expiry date must be on or after the offer date");
        }
        if (offerDao.hasOffer(applicationId)) {
            throw conflict("An offer already exists for this application");
        }
        long offerId = offerDao.insert(applicationId, request, userId);
        return offerDao.findById(offerId);
    }

    @Transactional
    public OfferResponse update(
            long offerId,
            OfferRequest request,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        OfferDao.OfferContext context = lockOffer(offerId);
        requireManagementAccess(context.assignedRecruiterId(), userId, administrator, placementOfficer);
        if (context.status() != OfferStatus.PENDING) {
            throw conflict("Only pending offers can be edited");
        }
        if (request.expiryDate().isBefore(request.offerDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Offer expiry date must be on or after the offer date");
        }
        offerDao.updateDetails(offerId, request);
        return offerDao.findById(offerId);
    }

    @Transactional
    public OfferResponse updateStatus(
            long offerId,
            OfferStatusRequest request,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        OfferDao.OfferContext context = lockOffer(offerId);
        requireManagementAccess(context.assignedRecruiterId(), userId, administrator, placementOfficer);
        if (!isValidTransition(context.status(), request.status())) {
            throw conflict("Invalid offer status transition from " + context.status()
                    + " to " + request.status());
        }
        if (request.status() == OfferStatus.EXPIRED
                && (context.expiryDate() == null || !context.expiryDate().isBefore(LocalDate.now()))) {
            throw conflict("An offer can be marked expired only after its expiry date");
        }
        if ((request.status() == OfferStatus.REJECTED
                || request.status() == OfferStatus.WITHDRAWN)
                && !hasText(request.reason())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A reason is required when rejecting or withdrawing an offer");
        }
        if (request.status() == OfferStatus.ACCEPTED
                && request.joiningDate() != null
                && request.joiningDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Joining date cannot be in the past");
        }
        if (!offerDao.updateStatus(
                offerId,
                context.status(),
                request.status(),
                request.joiningDate(),
                userId,
                request.reason())) {
            throw conflict("Offer status changed before the request could be completed");
        }
        if (request.status() == OfferStatus.ACCEPTED) {
            offerDao.updateApplicationStatus(
                    context.applicationId(),
                    "SELECTED",
                    "OFFERED",
                    userId,
                    "Offer accepted");
        } else if (request.status() == OfferStatus.REJECTED
                || request.status() == OfferStatus.EXPIRED) {
            offerDao.updateApplicationStatus(
                    context.applicationId(),
                    "SELECTED",
                    "REJECTED",
                    userId,
                    request.reason() == null ? "Offer expired" : request.reason());
        }
        return offerDao.findById(offerId);
    }

    @Transactional
    public OfferResponse updateJoiningDate(
            long offerId,
            OfferJoiningDateRequest request,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        OfferDao.OfferContext context = lockOffer(offerId);
        requireManagementAccess(context.assignedRecruiterId(), userId, administrator, placementOfficer);
        if (context.status() != OfferStatus.ACCEPTED) {
            throw conflict("Joining date can be recorded only for accepted offers");
        }
        offerDao.updateJoiningDate(offerId, request.joiningDate(), userId);
        return offerDao.findById(offerId);
    }

    @Transactional(readOnly = true)
    public OfferResponse getById(
            long offerId,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        OfferResponse offer = findByIdOrNotFound(offerId);
        requireReadAccess(offerId, userId, administrator, placementOfficer);
        return offer;
    }

    @Transactional(readOnly = true)
    public OfferPageResponse getOffers(
            String search,
            String candidate,
            String company,
            String designation,
            OfferStatus status,
            LocalDate expiryBefore,
            long userId,
            boolean recruiter,
            int page,
            int size) {
        return offerDao.findAll(
                search,
                candidate,
                company,
                designation,
                status,
                expiryBefore,
                recruiter ? userId : null,
                page,
                size);
    }

    @Transactional(readOnly = true)
    public List<OfferStatusHistoryResponse> getHistory(
            long offerId,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        findByIdOrNotFound(offerId);
        requireReadAccess(offerId, userId, administrator, placementOfficer);
        return offerDao.findHistory(offerId);
    }

    private OfferDao.OfferContext lockApplication(long applicationId) {
        try {
            return offerDao.lockApplication(applicationId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Application " + applicationId + " was not found");
        }
    }

    private OfferDao.OfferContext lockOffer(long offerId) {
        try {
            return offerDao.lockOffer(offerId);
        } catch (EmptyResultDataAccessException exception) {
            throw offerNotFound(offerId);
        }
    }

    private void requireReadAccess(
            long offerId,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        if (!administrator && !placementOfficer && !offerDao.recruiterOwnsOffer(offerId, userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Recruiters can access offers only for their assigned jobs");
        }
    }

    private static void requireManagementAccess(
            Long assignedRecruiterId,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        if (!administrator && !placementOfficer
                && (assignedRecruiterId == null || assignedRecruiterId != userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Recruiters can manage offers only for their assigned jobs");
        }
    }

    private static boolean isValidTransition(OfferStatus current, OfferStatus next) {
        return current == OfferStatus.PENDING
                && (next == OfferStatus.ACCEPTED
                || next == OfferStatus.REJECTED
                || next == OfferStatus.EXPIRED
                || next == OfferStatus.WITHDRAWN);
    }

    private OfferResponse findByIdOrNotFound(long offerId) {
        try {
            return offerDao.findById(offerId);
        } catch (EmptyResultDataAccessException exception) {
            throw offerNotFound(offerId);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException offerNotFound(long offerId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer " + offerId + " was not found");
    }
}
