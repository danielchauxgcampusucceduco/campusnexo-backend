package edu.campusnexo.application.disponibilidad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.application.alert.AlertRecorder;
import edu.campusnexo.application.port.in.CreateReservationCommand;
import edu.campusnexo.application.port.in.DisponibilidadUseCase.EspacioDisponible;
import edu.campusnexo.application.port.in.DisponibilidadUseCase.EstadoFranja;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.horario.SesionDeClase;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.domain.vision.BoundingBox;
import edu.campusnexo.domain.vision.DetectedObjectClass;
import edu.campusnexo.domain.vision.TrackedDetection;
import edu.campusnexo.support.InMemoryAlertRepository;
import edu.campusnexo.support.TwinTestFixture;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@code TestData.NOW} is 10:00 in Bogotá. */
class DisponibilidadServiceTest {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final TwinTestFixture fixture = new TwinTestFixture();
    private final InMemoryAlertRepository alerts = new InMemoryAlertRepository();
    private final LocalDate today = LocalDate.ofInstant(fixture.clock.instant(), BOGOTA);
    private final DayOfWeek weekday = today.getDayOfWeek();
    private final List<SesionDeClase> horario = List.of(
            new SesionDeClase("4A", 30, "Patrones de Software", weekday, 9, 11, "420-A", false),
            new SesionDeClase("6B", 20, "Redes", weekday, 14, 16, "420-A", false),
            new SesionDeClase("2B", 25, "Cálculo", weekday, 7, 9, "421-A", false));
    private final DisponibilidadService service = new DisponibilidadService(fixture.clock, fixture.catalog,
            () -> horario, fixture.reservationRows, fixture.twins, new AlertRecorder(alerts), BOGOTA,
            Duration.ofMinutes(15), Duration.ofMinutes(30));

    private EspacioDisponible espacio(String spaceId) {
        return service.disponibilidad(today).espacios().stream().filter(e -> e.spaceId().equals(spaceId)).findFirst()
                .orElseThrow();
    }

    private void camera(String spaceId, int people) {
        List<TrackedDetection> detections = new ArrayList<>();
        for (int i = 0; i < people; i++) {
            detections.add(new TrackedDetection(i, DetectedObjectClass.PERSON, 0.9, new BoundingBox(0.1, 0.4, 0.14, 0.47)));
        }
        fixture.pipeline.process(new TelemetrySnapshot(spaceId, fixture.clock.instant(), true, detections), "prueba");
    }

    @Test
    @DisplayName("Solo se ofrecen espacios de estudiantes, en franjas de una hora de 7:00 a 22:00")
    void studentSpacesHourByHour() {
        List<EspacioDisponible> espacios = service.disponibilidad(today).espacios();
        assertTrue(espacios.stream().noneMatch(e -> e.spaceId().equals("4A-GESTION-HUMANA")), "las oficinas no");
        assertTrue(espacios.stream().allMatch(e -> e.franjas().size() == 15));
        assertEquals(LocalTime.of(7, 0), espacios.getFirst().franjas().getFirst().desde());
    }

    @Test
    @DisplayName("Las clases del horario y las reservas ocupan sus franjas; el resto está libre")
    void timetableAndBookingsHoldTheirSlots() {
        fixture.reservations.create(new CreateReservationCommand("422-A",
                today.atTime(12, 0).atZone(BOGOTA).toInstant(), today.atTime(13, 0).atZone(BOGOTA).toInstant(), 10,
                "Tutoría"));
        var salon = espacio("420-A").franjas();
        assertEquals(EstadoFranja.CLASE, salon.get(2).estado(), "9:00–10:00");
        assertEquals("Patrones de Software · 4A", salon.get(2).detalle());
        assertEquals(EstadoFranja.LIBRE, salon.get(5).estado(), "12:00–13:00");
        assertEquals(EstadoFranja.CLASE, salon.get(7).estado(), "14:00–15:00");
        assertEquals(EstadoFranja.RESERVADO, espacio("422-A").franjas().get(5).estado());
    }

    @Test
    @DisplayName("Un salón libre ahora dice hasta cuándo; si la cámara ve personas, está ocupado")
    void freeUntilAndOccupiedNow() {
        EspacioDisponible calculo = espacio("421-A");
        assertEquals(EstadoFranja.LIBRE, calculo.ahora());
        assertEquals(LocalTime.of(22, 0), calculo.libreHasta(), "su clase ya pasó: libre hasta el cierre");

        camera("422-A", 4);
        EspacioDisponible ocupado = espacio("422-A");
        assertEquals(EstadoFranja.OCUPADO, ocupado.ahora());
        assertEquals(4, ocupado.personas());
        assertNull(ocupado.libreHasta());
    }

    @Test
    @DisplayName("Una clase en curso sin nadie tras la gracia queda como programada sin uso y avisa una vez")
    void scheduledButEmptyRaisesOneAlert() {
        camera("420-A", 0);
        EspacioDisponible vacio = espacio("420-A");
        assertEquals(EstadoFranja.PROGRAMADO_SIN_USO, vacio.ahora(), "la clase empezó a las 9 y son las 10");
        assertEquals(LocalTime.of(14, 0), vacio.libreHasta(), "libre hasta la siguiente clase");

        assertEquals(List.of("420-A"), service.vigilarHorario());
        assertEquals(List.of(), service.vigilarHorario(), "no se repite dentro del enfriamiento");
        assertEquals(AlertType.HORARIO_SIN_USO, alerts.rows().getFirst().type());
        assertTrue(alerts.rows().getFirst().message().contains("Patrones de Software"));
    }

    @Test
    @DisplayName("Sin cámara no se juzga: la clase sigue como clase y no hay alerta")
    void withoutCameraNothingIsJudged() {
        EspacioDisponible salon = espacio("420-A");
        assertFalse(salon.conCamara());
        assertEquals(EstadoFranja.CLASE, salon.ahora());
        assertTrue(service.vigilarHorario().isEmpty());
    }

    @Test
    @DisplayName("Con gente en clase no hay alerta, y antes de la gracia tampoco")
    void attendedOrEarlyIsFine() {
        camera("420-A", 12);
        assertTrue(service.vigilarHorario().isEmpty());
        assertEquals(EstadoFranja.OCUPADO, espacio("420-A").ahora());
    }
}
