package edu.campusnexo.infrastructure.adapter.in.web;

import edu.campusnexo.application.port.in.ReservationUseCase;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.infrastructure.adapter.in.web.dto.ReservationRequest;
import edu.campusnexo.infrastructure.adapter.in.web.dto.ReservationResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.BASE)
class ReservationController {
    private final ReservationUseCase reservations;

    ReservationController(ReservationUseCase reservations) {
        this.reservations = reservations;
    }

    @PostMapping("/spaces/{spaceId}/reservations")
    ResponseEntity<ReservationResponse> create(@PathVariable String spaceId,
                                               @Valid @RequestBody ReservationRequest request) {
        Reservation reservation = reservations.create(request.toCommand(spaceId));
        URI location = URI.create(ApiPaths.BASE + "/reservations/" + reservation.reservationId());
        return ResponseEntity.created(location).body(ReservationResponse.from(reservation));
    }

    @GetMapping("/spaces/{spaceId}/reservations")
    List<ReservationResponse> list(@PathVariable String spaceId,
                                   @RequestParam(required = false) Instant from,
                                   @RequestParam(required = false) Instant to) {
        return reservations.list(spaceId, from, to).stream().map(ReservationResponse::from).toList();
    }

    @GetMapping("/reservations/{reservationId}")
    ReservationResponse get(@PathVariable UUID reservationId) {
        return ReservationResponse.from(reservations.get(reservationId));
    }

    @DeleteMapping("/reservations/{reservationId}")
    ResponseEntity<Void> cancel(@PathVariable UUID reservationId) {
        reservations.cancel(reservationId);
        return ResponseEntity.noContent().build();
    }
}
