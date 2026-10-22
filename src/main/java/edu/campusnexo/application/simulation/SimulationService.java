package edu.campusnexo.application.simulation;

import edu.campusnexo.application.exception.ConflictException;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.CreateReservationCommand;
import edu.campusnexo.application.port.in.ProcessEnvironmentReadingUseCase;
import edu.campusnexo.application.port.in.ProcessSpaceTelemetryUseCase;
import edu.campusnexo.application.port.in.Requester;
import edu.campusnexo.application.port.in.ReservationUseCase;
import edu.campusnexo.application.port.in.SimulationResult;
import edu.campusnexo.application.port.in.SimulationScenario;
import edu.campusnexo.application.port.in.SimulationUseCase;
import edu.campusnexo.application.port.in.SpaceTwinView;
import edu.campusnexo.application.port.in.TwinControlUseCase;
import edu.campusnexo.application.port.in.TwinQueryUseCase;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.reservation.ReservationOrigin;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.twin.EnvironmentReading;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Demonstration scenarios for the defense. A scenario only ever touches the rooms marked for the
 * demonstration in the catalog ({@code simulationAllowed}), never the rest of the campus, and it goes
 * exclusively through the input ports that real devices and people use: it resets the room, replays the
 * camera frames and Arduino readings that lead to the situation (for example, an empty room seen eight
 * minutes ago and now), creates its reservations with origin {@code SIMULACION}, and applies manual
 * controls. Every precondition is checked before the first write, so a scenario that cannot run changes
 * nothing.
 */
public class SimulationService implements SimulationUseCase {
    private static final Duration RESERVATION_SWEEP = Duration.ofHours(13);

    private final Clock clock;
    private final ProcessSpaceTelemetryUseCase pipeline;
    private final ProcessEnvironmentReadingUseCase environment;
    private final TwinControlUseCase controls;
    private final ReservationUseCase reservations;
    private final CampusCatalogUseCase catalog;
    private final TwinQueryUseCase queries;

    public SimulationService(Clock clock, ProcessSpaceTelemetryUseCase pipeline,
                             ProcessEnvironmentReadingUseCase environment, TwinControlUseCase controls,
                             ReservationUseCase reservations, CampusCatalogUseCase catalog, TwinQueryUseCase queries) {
        this.clock = clock;
        this.pipeline = pipeline;
        this.environment = environment;
        this.controls = controls;
        this.reservations = reservations;
        this.catalog = catalog;
        this.queries = queries;
    }

    @Override
    public SimulationResult run(SimulationScenario scenario, String source) {
        Instant now = clock.instant();
        if (scenario == SimulationScenario.REINICIAR) {
            for (CampusSpace space : demoSpaces()) {
                clearSimulationReservations(space.spaceId(), now.minus(RESERVATION_SWEEP), now.plus(RESERVATION_SWEEP));
                controls.reset(space.spaceId());
            }
            return new SimulationResult(scenario, queries.spaces().stream()
                    .filter(view -> view.space().simulationAllowed()).toList(), null);
        }
        CampusSpace room = requireDemoRoom(scenario.spaceId());
        Scenarios plan = new Scenarios(room, now, source);
        plan.run(scenario);
        return new SimulationResult(scenario, List.of(queries.space(room.spaceId())), latestTrace(room.spaceId()));
    }

    private List<CampusSpace> demoSpaces() {
        return catalog.listSpaces(null).stream().filter(CampusSpace::simulationAllowed).toList();
    }

    private CampusSpace requireDemoRoom(String spaceId) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        if (!space.simulationAllowed()) {
            throw new ConflictException("simulation_space_not_allowed",
                    "El espacio " + spaceId + " no está habilitado para la demostración");
        }
        return space;
    }

    private ArchitectureTrace latestTrace(String spaceId) {
        return queries.recentTraces(50).stream().filter(trace -> trace.spaceId().equals(spaceId)).findFirst().orElse(null);
    }

    private void clearSimulationReservations(String spaceId, Instant from, Instant to) {
        for (Reservation reservation : reservations.list(spaceId, from, to)) {
            if (reservation.createdBySimulation()) {
                reservations.cancel(reservation.reservationId(), Requester.simulation());
            }
        }
    }

    /** The steps of each scenario for one room at one instant. */
    private final class Scenarios {
        private final CampusSpace room;
        private final String id;
        private final Instant now;
        private final String source;
        private final SyntheticFrames frames;

        Scenarios(CampusSpace room, Instant now, String source) {
            this.room = room;
            this.id = room.spaceId();
            this.now = now;
            this.source = source;
            this.frames = new SyntheticFrames(id);
        }

        void run(SimulationScenario scenario) {
            switch (scenario) {
                case CLASE_EN_CURSO -> {
                    Window booking = reserveWindow(minutes(-10), minutes(80));
                    fresh();
                    reserve(booking, 28, scenario);
                    camera(frames.seated(seats(), now, 26, 780, 22.4, 55, 420, 30, 0.8));
                    arduino(now, 22.4, 55, 780.0, 420, true, 0);
                }
                case CO2_ALTO -> {
                    fresh();
                    camera(frames.seated(seats(), now, 30, 1450, 21.2, 62, 400, 33, 0.9));
                    arduino(now, 21.2, 62, 1450.0, 400, true, 0);
                }
                case FALLO_CAMARA -> {
                    fresh();
                    camera(frames.seated(seats(), now.minus(minutes(2)), 22, 820, 20.4, 57, 410, 22, 0.8));
                    camera(frames.cameraOffline(now, 820, 20.3, 57, 410, 22, 0.8));
                }
                case SOBRECUPO -> {
                    int crowd = room.capacity() + 7;
                    fresh();
                    camera(frames.seated(seats(), now, crowd, 980, 21.8, 58, 520, crowd, 2.4));
                    arduino(now, 21.8, 58, 980.0, 520, true, room.computers());
                }
                case NO_SHOW -> {
                    Window booking = reserveWindow(minutes(-20), minutes(100));
                    fresh();
                    reserve(booking, 30, scenario);
                    camera(frames.seated(seats(), now, 0, 440, 18.6, 54, 60, 1, 0.1));
                }
                case SUBUTILIZADO -> {
                    Window booking = reserveWindow(minutes(-10), minutes(80));
                    fresh();
                    reserve(booking, 30, scenario);
                    camera(frames.seated(seats(), now, 6, 520, 19.4, 60, 410, 7, 0.5));
                }
                case POPUP_PARCIALES -> {
                    fresh();
                    controls.openPopUpStudyRoom(id, Duration.ofHours(2));
                    camera(frames.seated(seats(), now, 9, 560, 19.8, 58, 450, 10, 0.4));
                }
                case LUCES_ENCENDIDAS -> idleRoom(minutes(8), 0);
                case EQUIPOS_ENCENDIDOS -> idleRoom(minutes(12), room.computers());
                case OVERRIDE_DOCENTE -> controls.manualOverride(id, minutes(45));
                case MANTENIMIENTO -> controls.setMaintenance(id, true);
                case ACAPARAMIENTO_BIBLIOTECA -> libraryHoarding();
                case REINICIAR -> throw new IllegalStateException("REINICIAR se atiende antes");
            }
        }

        /** The room starts from scratch, without the demonstration's earlier bookings. */
        private void fresh() {
            clearSimulationReservations(id, now.minus(RESERVATION_SWEEP), now.plus(RESERVATION_SWEEP));
            controls.reset(id);
        }

        /**
         * An empty room seen {@code idle} ago with the lights (and computers) on, and seen again now: the
         * same two edge frames (camera plus the room board's relays) a real room would produce. The second
         * one crosses the idle threshold and the twin orders the shutdown.
         */
        private void idleRoom(Duration idle, int devicesOn) {
            fresh();
            camera(frames.withEquipment(frames.seated(seats(), now.minus(idle), 0, 430, 18.9, 52, 380, 0,
                    devicesOn * 0.08), true, devicesOn));
            camera(frames.withEquipment(frames.seated(seats(), now, 0, 430, 18.9, 52, 380, 0, devicesOn * 0.08),
                    true, devicesOn));
        }

        /**
         * A busy library: tables 3–5 have students and 30 more walk along the aisle the whole time. Table 1 has
         * had belongings without its student for 16 minutes (released) and table 2 for 6 minutes (alert).
         * Replayed as three frames: 16 minutes ago, 6 minutes ago and now.
         */
        private void libraryHoarding() {
            List<StudyZoneRoi> desks = seats();
            if (desks.size() < 5) {
                throw new ConflictException("simulation_precondition_failed",
                        "La biblioteca necesita al menos 5 puestos con ROI para la simulación");
            }
            fresh();
            camera(frames.library(desks, now.minus(minutes(16)), List.of(0), List.of(2, 3, 4), 30, room.areaSqm()));
            camera(frames.library(desks, now.minus(minutes(6)), List.of(0, 1), List.of(2, 3, 4), 30, room.areaSqm()));
            camera(frames.library(desks, now, List.of(0, 1), List.of(2, 3, 4), 30, room.areaSqm()));
            arduino(now, 19.6, 52, 760.0, 450, true, 6);
        }

        private List<StudyZoneRoi> seats() {
            SpaceTwinView view = queries.space(id);
            return view.rois();
        }

        private Window reserveWindow(Duration fromNow, Duration toNow) {
            Window window = new Window(now.plus(fromNow), now.plus(toNow));
            boolean takenByPeople = reservations.list(id, window.start(), window.end()).stream()
                    .anyMatch(reservation -> !reservation.createdBySimulation());
            if (takenByPeople) {
                throw new ConflictException("simulation_blocked_by_reservation",
                        "El salón " + id + " tiene una reserva real en ese horario; el escenario no se ejecuta");
            }
            return window;
        }

        private void reserve(Window window, int attendees, SimulationScenario scenario) {
            reservations.create(new CreateReservationCommand(id, window.start(), window.end(), attendees,
                    "SIM-" + scenario.name(), ReservationOrigin.SIMULACION, Requester.simulation()));
        }

        private void camera(TelemetrySnapshot snapshot) {
            pipeline.process(snapshot, source);
        }

        private void arduino(Instant at, double temperatureC, double humidityPct, Double co2Ppm, double lux,
                             boolean lightsOn, int devicesOn) {
            environment.processEnvironment(new EnvironmentReading(id, at, temperatureC, humidityPct, co2Ppm, lux,
                    lightsOn, devicesOn), source + " (lectura Arduino)");
        }
    }

    private record Window(Instant start, Instant end) {
    }

    private static Duration minutes(long value) {
        return Duration.ofMinutes(value);
    }
}
