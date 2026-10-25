package edu.campusnexo.application.telemetry;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.RecordTelemetryCommand;
import edu.campusnexo.application.port.in.TelemetryUseCase;
import edu.campusnexo.application.port.out.UnitOfWork;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.TelemetryRepository;
import edu.campusnexo.domain.shared.PageCursor;
import edu.campusnexo.domain.shared.ResultLimit;
import edu.campusnexo.domain.telemetry.ReadingAdmission;
import edu.campusnexo.domain.telemetry.Telemetry;
import edu.campusnexo.domain.telemetry.TelemetryRecorded;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stores aggregated readings. A reading per space and instant is kept once (idempotent ingestion, enforced by
 * a unique key in PostgreSQL as well), and the alert observers are notified only after the reading commits.
 */
public class TelemetryService implements TelemetryUseCase {
    private final Clock clock;
    private final TelemetryRepository repository;
    private final CampusCatalogUseCase catalog;
    private final TelemetryEventPublisher publisher;
    private final ReadingAdmission admission;
    private final UnitOfWork unitOfWork;

    public TelemetryService(Clock clock, TelemetryRepository repository, CampusCatalogUseCase catalog,
                            TelemetryEventPublisher publisher, ReadingAdmission admission, UnitOfWork unitOfWork) {
        this.clock = clock;
        this.repository = repository;
        this.catalog = catalog;
        this.publisher = publisher;
        this.admission = admission;
        this.unitOfWork = unitOfWork;
    }

    /** Without transactions: for tests and tools. */
    public TelemetryService(Clock clock, TelemetryRepository repository, CampusCatalogUseCase catalog,
                            TelemetryEventPublisher publisher, Duration maxFutureSkew, Duration maxAge) {
        this(clock, repository, catalog, publisher, new ReadingAdmission(maxFutureSkew, maxAge), UnitOfWork.direct());
    }

    @Override
    public IngestResult ingest(RecordTelemetryCommand command) {
        CampusSpace space = catalog.getRequiredSpace(command.spaceId());
        Instant now = clock.instant();
        Instant recordedAt = command.recordedAt() == null ? now : command.recordedAt();
        admission.check(recordedAt, now);
        Telemetry telemetry = new Telemetry(space.spaceId(), recordedAt, command.peopleCount(), command.objectCount(),
                command.wifiDevices(), command.co2Ppm(), command.temperatureC(), command.humidityPct(),
                command.illuminanceLux(), command.activePowerKw(), command.cameraPeople());

        Optional<Telemetry> duplicate = repository.findBySpaceAndRecordedAt(space.spaceId(), recordedAt);
        if (duplicate.isPresent()) {
            return new IngestResult(duplicate.get(), false);
        }
        Telemetry saved = repository.save(telemetry);
        unitOfWork.afterCommit(() -> publisher.publish(new TelemetryRecorded(saved, space)));
        return new IngestResult(saved, true);
    }

    @Override
    public List<Telemetry> recent(String spaceId, int limit, PageCursor before) {
        ResultLimit resultLimit = ResultLimit.of(limit);
        String filter = normalize(spaceId);
        if (filter != null) {
            catalog.getRequiredSpace(filter);
        }
        return repository.findRecent(filter, resultLimit, before);
    }

    @Override
    public Optional<Telemetry> latest(String spaceId) {
        catalog.getRequiredSpace(spaceId);
        return repository.findLatest(spaceId);
    }

    private static String normalize(String spaceId) {
        return spaceId == null || spaceId.isBlank() ? null : spaceId;
    }
}
