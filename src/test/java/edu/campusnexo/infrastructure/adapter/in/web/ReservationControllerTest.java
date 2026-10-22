package edu.campusnexo.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.campusnexo.application.port.in.Requester;
import edu.campusnexo.application.port.in.ReservationUseCase;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.infrastructure.adapter.in.web.dto.ReservationRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ReservationControllerTest {
    @Test
    void createsReservationWithLocationAndReturnsNoContentOnCancellation() {
        ReservationUseCase useCase = mock(ReservationUseCase.class);
        UUID id = UUID.fromString("85b84f8a-76ef-4fd2-9d69-ae1a1c264e22");
        Instant starts = Instant.parse("2026-10-21T13:00:00Z");
        Reservation saved = new Reservation(id, "A-301", starts, starts.plusSeconds(3600), 20, "IS-101");
        when(useCase.create(any())).thenReturn(saved);
        ReservationController controller = new ReservationController(useCase);

        Requester operator = Requester.user("operador");
        var created = controller.create("A-301", new ReservationRequest(starts, starts.plusSeconds(3600), 20, "IS-101"),
                operator);
        var deleted = controller.cancel(id, operator);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals("/api/v1/reservations/" + id, created.getHeaders().getLocation().getPath());
        assertEquals(id, created.getBody().reservationId());
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());
        verify(useCase).cancel(id, operator);
    }
}
