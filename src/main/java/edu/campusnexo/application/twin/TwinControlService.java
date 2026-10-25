package edu.campusnexo.application.twin;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.TwinControlUseCase;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Manual controls of a room. Each one is applied and the room decided again in a single write. */
public class TwinControlService implements TwinControlUseCase {
    static final Duration MAX_CONTROL_DURATION = Duration.ofHours(12);
    static final String TRIGGER = "REST · TwinControlUseCase";

    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final SpaceReevaluationService reevaluation;

    public TwinControlService(Clock clock, CampusCatalogUseCase catalog, SpaceReevaluationService reevaluation) {
        this.clock = clock;
        this.catalog = catalog;
        this.reevaluation = reevaluation;
    }

    @Override
    public SpaceTwinState manualOverride(String spaceId, Duration duration) {
        Instant until = clock.instant().plus(validated(duration));
        return reevaluation.reevaluate(spaceId, TRIGGER,
                twin -> twin.withControls(twin.controls().withOverrideUntil(until)));
    }

    @Override
    public SpaceTwinState openPopUpStudyRoom(String spaceId, Duration duration) {
        Instant until = clock.instant().plus(validated(duration));
        return reevaluation.reevaluate(spaceId, TRIGGER, twin -> twin.withControls(twin.controls().withPopUpUntil(until)));
    }

    @Override
    public SpaceTwinState setMaintenance(String spaceId, boolean maintenance) {
        return reevaluation.reevaluate(spaceId, TRIGGER,
                twin -> twin.withControls(twin.controls().withMaintenance(maintenance)));
    }

    /** Forgets everything the twin learned about the room: it starts again as if it had never reported. */
    @Override
    public SpaceTwinState reset(String spaceId) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        return reevaluation.reevaluate(spaceId, TRIGGER, ignored -> SpaceTwinState.initial(space, clock.instant()));
    }

    private static Duration validated(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero() || duration.compareTo(MAX_CONTROL_DURATION) > 0) {
            throw new DomainValidationException("La duración debe estar entre 1 minuto y " + MAX_CONTROL_DURATION.toHours() + " horas");
        }
        return duration;
    }
}
