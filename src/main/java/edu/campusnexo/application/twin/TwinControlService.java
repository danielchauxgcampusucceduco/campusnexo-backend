package edu.campusnexo.application.twin;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.SpaceReevaluationUseCase;
import edu.campusnexo.application.port.in.TwinControlUseCase;
import edu.campusnexo.domain.catalog.CampusSpace;
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
    private final TwinWriter writer;
    private final SpaceReevaluationUseCase reevaluation;

    public TwinControlService(Clock clock, CampusCatalogUseCase catalog, TwinWriter writer,
                              SpaceReevaluationUseCase reevaluation) {
        this.clock = clock;
        this.catalog = catalog;
        this.writer = writer;
        this.reevaluation = reevaluation;
    }

    @Override
    public SpaceTwinState manualOverride(String spaceId, Duration duration) {
        Instant until = clock.instant().plus(validated(duration));
        change(spaceId, twin -> twin.withControls(until, twin.popUpUntil(), twin.maintenance()));
        return reevaluation.reevaluate(spaceId, TRIGGER);
    }

    @Override
    public SpaceTwinState openPopUpStudyRoom(String spaceId, Duration duration) {
        Instant until = clock.instant().plus(validated(duration));
        change(spaceId, twin -> twin.withControls(twin.overrideUntil(), until, twin.maintenance()));
        return reevaluation.reevaluate(spaceId, TRIGGER);
    }

    @Override
    public SpaceTwinState setMaintenance(String spaceId, boolean maintenance) {
        change(spaceId, twin -> twin.withControls(twin.overrideUntil(), twin.popUpUntil(), maintenance));
        return reevaluation.reevaluate(spaceId, TRIGGER);
    }

    @Override
    public SpaceTwinState reset(String spaceId) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        writer.replace(spaceId, () -> SpaceTwinState.initial(space, clock.instant()),
                ignored -> SpaceTwinState.initial(space, clock.instant()));
        return reevaluation.reevaluate(spaceId, TRIGGER);
    }

    private void change(String spaceId, java.util.function.UnaryOperator<SpaceTwinState> control) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        writer.replace(spaceId, () -> SpaceTwinState.initial(space, clock.instant()), control);
    }

    private static Duration validated(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero() || duration.compareTo(MAX_CONTROL_DURATION) > 0) {
            throw new DomainValidationException("La duración debe estar entre 1 minuto y " + MAX_CONTROL_DURATION.toHours() + " horas");
        }
        return duration;
    }
}
