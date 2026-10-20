package edu.campusnexo.application.twin;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.SpaceReevaluationUseCase;
import edu.campusnexo.application.port.in.TwinControlUseCase;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class TwinControlService implements TwinControlUseCase {
    static final Duration MAX_CONTROL_DURATION = Duration.ofHours(12);
    private static final String TRIGGER = "REST · TwinControlUseCase";

    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final SpaceRepositoryPort twins;
    private final SpaceReevaluationUseCase reevaluation;

    public TwinControlService(Clock clock, CampusCatalogUseCase catalog, SpaceRepositoryPort twins,
                              SpaceReevaluationUseCase reevaluation) {
        this.clock = clock;
        this.catalog = catalog;
        this.twins = twins;
        this.reevaluation = reevaluation;
    }

    @Override
    public SpaceTwinState manualOverride(String spaceId, Duration duration) {
        SpaceTwinState twin = current(spaceId);
        Instant until = clock.instant().plus(validated(duration));
        twins.saveTwin(twin.withControls(until, twin.popUpUntil(), twin.maintenance()));
        return reevaluation.reevaluate(spaceId, TRIGGER);
    }

    @Override
    public SpaceTwinState openPopUpStudyRoom(String spaceId, Duration duration) {
        SpaceTwinState twin = current(spaceId);
        Instant until = clock.instant().plus(validated(duration));
        twins.saveTwin(twin.withControls(twin.overrideUntil(), until, twin.maintenance()));
        return reevaluation.reevaluate(spaceId, TRIGGER);
    }

    @Override
    public SpaceTwinState setMaintenance(String spaceId, boolean maintenance) {
        SpaceTwinState twin = current(spaceId);
        twins.saveTwin(twin.withControls(twin.overrideUntil(), twin.popUpUntil(), maintenance));
        return reevaluation.reevaluate(spaceId, TRIGGER);
    }

    @Override
    public SpaceTwinState reset(String spaceId) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        twins.saveTwin(SpaceTwinState.initial(space, clock.instant()));
        return reevaluation.reevaluate(spaceId, TRIGGER);
    }

    private SpaceTwinState current(String spaceId) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        return twins.findTwin(spaceId).orElseGet(() -> SpaceTwinState.initial(space, clock.instant()));
    }

    private static Duration validated(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero() || duration.compareTo(MAX_CONTROL_DURATION) > 0) {
            throw new DomainValidationException("La duración debe estar entre 1 minuto y " + MAX_CONTROL_DURATION.toHours() + " horas");
        }
        return duration;
    }
}
