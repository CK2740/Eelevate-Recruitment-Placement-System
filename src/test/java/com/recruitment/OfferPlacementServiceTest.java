package com.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recruitment.dao.OfferDao;
import com.recruitment.dao.PlacementDao;
import com.recruitment.dto.OfferPageResponse;
import com.recruitment.dto.OfferRequest;
import com.recruitment.dto.OfferResponse;
import com.recruitment.dto.OfferStatus;
import com.recruitment.dto.OfferStatusRequest;
import com.recruitment.dto.PlacementPageResponse;
import com.recruitment.dto.PlacementRequest;
import com.recruitment.dto.PlacementResponse;
import com.recruitment.dto.PlacementStatus;
import com.recruitment.service.OfferService;
import com.recruitment.service.PlacementService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OfferPlacementServiceTest {

    @Mock
    private OfferDao offerDao;

    @Mock
    private PlacementDao placementDao;

    @InjectMocks
    private OfferService offerService;

    @Test
    void createsOfferForSelectedApplication() {
        when(offerDao.lockApplication(15)).thenReturn(selectedApplication());
        when(offerDao.hasOffer(15)).thenReturn(false);
        when(offerDao.insert(15, offerRequest(), 7)).thenReturn(30L);
        when(offerDao.findById(30)).thenReturn(offer(30, OfferStatus.PENDING));

        assertEquals(offer(30, OfferStatus.PENDING),
                offerService.create(15, offerRequest(), 7, false, false));
        verify(offerDao).insert(15, offerRequest(), 7);
    }

    @Test
    void offerRequiresSelectedApplicationAndCannotBeDuplicated() {
        when(offerDao.lockApplication(15))
                .thenReturn(new OfferDao.OfferContext(15, "APPLIED", 7L));
        ResponseStatusException notSelected = assertThrows(
                ResponseStatusException.class,
                () -> offerService.create(15, offerRequest(), 7, false, false));
        assertEquals(HttpStatus.CONFLICT, notSelected.getStatusCode());

        when(offerDao.lockApplication(15)).thenReturn(selectedApplication());
        when(offerDao.hasOffer(15)).thenReturn(true);
        ResponseStatusException duplicate = assertThrows(
                ResponseStatusException.class,
                () -> offerService.create(15, offerRequest(), 7, false, false));
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
        verify(offerDao, never()).insert(15, offerRequest(), 7);
    }

    @Test
    void recruiterCanManageOnlyOffersForAssignedJobs() {
        when(offerDao.lockApplication(15))
                .thenReturn(new OfferDao.OfferContext(15, "SELECTED", 8L));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> offerService.create(15, offerRequest(), 7, false, false));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void acceptsOrRejectsPendingOffersAndEnforcesTransitions() {
        when(offerDao.lockOffer(30)).thenReturn(offerContext(OfferStatus.PENDING));
        when(offerDao.updateStatus(30, OfferStatus.PENDING, OfferStatus.ACCEPTED,
                LocalDate.now().plusDays(40), 7, null)).thenReturn(true);
        when(offerDao.findById(30)).thenReturn(offer(30, OfferStatus.ACCEPTED));

        assertEquals(OfferStatus.ACCEPTED, offerService.updateStatus(
                30,
                new OfferStatusRequest(OfferStatus.ACCEPTED, null, LocalDate.now().plusDays(40)),
                7,
                false,
                false).status());
        verify(offerDao).updateApplicationStatus(15, "SELECTED", "OFFERED", 7, "Offer accepted");

        when(offerDao.lockOffer(30)).thenReturn(offerContext(OfferStatus.ACCEPTED));
        ResponseStatusException invalidTransition = assertThrows(
                ResponseStatusException.class,
                () -> offerService.updateStatus(
                        30, new OfferStatusRequest(OfferStatus.REJECTED, "No", null), 7, false, false));
        assertEquals(HttpStatus.CONFLICT, invalidTransition.getStatusCode());
    }

    @Test
    void rejectsExpiredOfferTransitionsBeforeExpiryAndRequiresRejectionReason() {
        when(offerDao.lockOffer(30)).thenReturn(offerContext(OfferStatus.PENDING));
        ResponseStatusException missingReason = assertThrows(
                ResponseStatusException.class,
                () -> offerService.updateStatus(
                        30, new OfferStatusRequest(OfferStatus.REJECTED, " ", null), 7, false, false));
        assertEquals(HttpStatus.BAD_REQUEST, missingReason.getStatusCode());

        ResponseStatusException notYetExpired = assertThrows(
                ResponseStatusException.class,
                () -> offerService.updateStatus(
                        30, new OfferStatusRequest(OfferStatus.EXPIRED, null, null), 7, false, false));
        assertEquals(HttpStatus.CONFLICT, notYetExpired.getStatusCode());
    }

    @Test
    void offerListUsesPaginationAndFilters() {
        OfferPageResponse expected = new OfferPageResponse(List.of(offer(30, OfferStatus.PENDING)), 1, 10, 11, 2);
        when(offerDao.findAll("Ada", "Ada", "Acme", "Engineer",
                OfferStatus.PENDING, LocalDate.now().plusDays(2), 7L, 1, 10))
                .thenReturn(expected);

        assertEquals(expected, offerService.getOffers(
                "Ada", "Ada", "Acme", "Engineer", OfferStatus.PENDING,
                LocalDate.now().plusDays(2), 7, true, 1, 10));
    }

    @InjectMocks
    private PlacementService placementService;

    @Test
    void createsPlacementAfterAcceptedOfferAndPreventsDuplicates() {
        when(placementDao.lockApplication(15)).thenReturn(acceptedApplication());
        when(placementDao.placementExists(15)).thenReturn(false);
        when(placementDao.insert(15, placementRequest(), 9)).thenReturn(40L);
        when(placementDao.findById(40)).thenReturn(placement(40, PlacementStatus.PENDING));

        assertEquals(placement(40, PlacementStatus.PENDING),
                placementService.create(15, placementRequest(), 9));

        when(placementDao.placementExists(15)).thenReturn(true);
        ResponseStatusException duplicate = assertThrows(
                ResponseStatusException.class,
                () -> placementService.create(15, placementRequest(), 9));
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
    }

    @Test
    void placementRequiresAcceptedOfferAndJoiningDateWhenJoined() {
        when(placementDao.lockApplication(15)).thenReturn(selectedApplicationForPlacement());
        ResponseStatusException notAccepted = assertThrows(
                ResponseStatusException.class,
                () -> placementService.create(15, placementRequest(), 9));
        assertEquals(HttpStatus.CONFLICT, notAccepted.getStatusCode());

        when(placementDao.lockApplication(15)).thenReturn(acceptedApplication());
        PlacementRequest missingDate = new PlacementRequest(
                "Software Engineer", new BigDecimal("90000"), null, PlacementStatus.JOINED);
        ResponseStatusException dateRequired = assertThrows(
                ResponseStatusException.class,
                () -> placementService.create(15, missingDate, 9));
        assertEquals(HttpStatus.BAD_REQUEST, dateRequired.getStatusCode());
    }

    @Test
    void recordsPlacementJoiningWorkflowAndHistory() {
        when(placementDao.lockPlacement(40)).thenReturn(placementContext(PlacementStatus.PENDING));
        when(placementDao.update(40, PlacementStatus.PENDING, joinedPlacementRequest(), 9, "Started work"))
                .thenReturn(true);
        when(placementDao.findById(40)).thenReturn(placement(40, PlacementStatus.JOINED));

        assertEquals(PlacementStatus.JOINED, placementService.update(
                40, joinedPlacementRequest(), "Started work", 9).joiningStatus());
        verify(placementDao).update(
                40, PlacementStatus.PENDING, joinedPlacementRequest(), 9, "Started work");

        when(placementDao.lockPlacement(40)).thenReturn(placementContext(PlacementStatus.JOINED));
        ResponseStatusException invalidTransition = assertThrows(
                ResponseStatusException.class,
                () -> placementService.update(
                        40,
                        new PlacementRequest(
                                "Software Engineer",
                                new BigDecimal("90000"),
                                LocalDate.now(),
                                PlacementStatus.NOT_JOINED),
                        "Correction",
                        9));
        assertEquals(HttpStatus.CONFLICT, invalidTransition.getStatusCode());
    }

    @Test
    void duplicatePlacementDatabaseConstraintReturnsConflict() {
        when(placementDao.lockApplication(15)).thenReturn(acceptedApplication());
        when(placementDao.placementExists(15)).thenReturn(false);
        when(placementDao.insert(15, placementRequest(), 9))
                .thenThrow(new DuplicateKeyException("duplicate application placement"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> placementService.create(15, placementRequest(), 9));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void placementListSupportsSearchAndFilters() {
        PlacementPageResponse expected = new PlacementPageResponse(
                List.of(placement(40, PlacementStatus.PENDING)), 0, 20, 1, 1);
        when(placementDao.findAll("Ada", "Ada", "Acme", "Engineer",
                PlacementStatus.PENDING, null, 0, 20)).thenReturn(expected);

        assertEquals(expected, placementService.getPlacements(
                "Ada", "Ada", "Acme", "Engineer", PlacementStatus.PENDING, null, 0, 20));
    }

    private static OfferDao.OfferContext selectedApplication() {
        return new OfferDao.OfferContext(15, "SELECTED", 7L);
    }

    private static OfferDao.OfferContext offerContext(OfferStatus status) {
        return new OfferDao.OfferContext(
                30, 15, status, LocalDate.now().plusDays(10), null,
                "Software Engineer", new BigDecimal("90000"), 7L, "SELECTED");
    }

    private static PlacementDao.PlacementContext acceptedApplication() {
        return new PlacementDao.PlacementContext(
                15, "OFFERED", 7L, "ACCEPTED", LocalDate.now().plusDays(20),
                "Software Engineer", new BigDecimal("90000"));
    }

    private static PlacementDao.PlacementContext selectedApplicationForPlacement() {
        return new PlacementDao.PlacementContext(
                15, "SELECTED", 7L, "PENDING", null,
                "Software Engineer", new BigDecimal("90000"));
    }

    private static PlacementDao.PlacementContext placementContext(PlacementStatus status) {
        return new PlacementDao.PlacementContext(
                15, "OFFERED", 7L, null, null, null, null, 40, status);
    }

    private static OfferRequest offerRequest() {
        return new OfferRequest(
                "Software Engineer",
                new BigDecimal("90000"),
                LocalDate.now(),
                LocalDate.now().plusDays(10),
                "Permanent role");
    }

    private static PlacementRequest placementRequest() {
        return new PlacementRequest(
                "Software Engineer", new BigDecimal("90000"), null, PlacementStatus.PENDING);
    }

    private static PlacementRequest joinedPlacementRequest() {
        return new PlacementRequest(
                "Software Engineer", new BigDecimal("90000"), LocalDate.now(), PlacementStatus.JOINED);
    }

    private static OfferResponse offer(long id, OfferStatus status) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 10, 0);
        return new OfferResponse(
                id,
                15,
                4,
                "Ada Lovelace",
                3,
                "Acme Inc",
                "Software Engineer",
                new BigDecimal("90000"),
                LocalDate.now(),
                LocalDate.now().plusDays(10),
                null,
                "Permanent role",
                status,
                timestamp,
                timestamp);
    }

    private static PlacementResponse placement(long id, PlacementStatus status) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 10, 0);
        return new PlacementResponse(
                id,
                15,
                4,
                "Ada Lovelace",
                3,
                "Acme Inc",
                "Software Engineer",
                new BigDecimal("90000"),
                status == PlacementStatus.JOINED ? LocalDate.now() : null,
                status,
                timestamp,
                timestamp);
    }
}
