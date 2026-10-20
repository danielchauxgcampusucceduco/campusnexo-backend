package edu.campusnexo.application.telemetry;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.RecordTelemetryCommand;
import edu.campusnexo.application.port.in.TelemetryUseCase;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.TelemetryRepository;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.shared.ResultLimit;
import edu.campusnexo.domain.telemetry.Telemetry;
import edu.campusnexo.domain.telemetry.TelemetryRecorded;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class TelemetryService implements TelemetryUseCase {
    private final Clock clock;
    private final TelemetryRepository repository;
    private final CampusCatalogUseCase catalog;
    private final TelemetryEventPublisher publisher;
    private final Duration maxFutureSkew;
    private final Duration maxAge;

    public TelemetryService(Clock clock, TelemetryRepository repository, CampusCatalogUseCase catalog,
                            TelemetryEventPublisher publisher, Duration maxFutureSkew, Duration maxAge) {
        this.clock = clock;
        this.repository = repository;
        this.catalog = catalog;
        this.publisher = publisher;
        this.maxFutureSkew = maxFutureSkew;
        this.maxAge = maxAge;
    }

    @Override
    public IngestResult ingest(RecordTelemetryCommand command) {
        CampusSpace space = catalog.getRequiredSpace(command.spaceId());
        Instant now = clock.instant();
        Instant recordedAt = command.recordedAt() == null ? now : command.recordedAt();
        if (recordedAt.isAfter(now.plus(maxFutureSkew))) {
            throw new DomainValidationException("recordedAt no puede estar en el futuro");
        }
        if (recordedAt.isBefore(now.minus(maxAge))) {
            throw new DomainValidationException("recordedAt es más antiguo que " + maxAge.toDays() + " días");
        }
        Telemetry telemetry = new Telemetry(space.spaceId(), recordedAt, command.peopleCount(), command.objectCount(),
                command.wifiDevices(), command.co2Ppm(), command.temperatureC(), command.humidityPct(),
                command.illuminanceLux(), command.activePowerKw());

        Optional<Telemetry> duplicate = repository.findBySpaceAndRecordedAt(space.spaceId(), recordedAt);
        if (duplicate.isPresent()) {
            return new IngestResult(duplicate.get(), false);
        }
        Telemetry saved = repository.save(telemetry);
        publisher.publish(new TelemetryRecorded(saved, space));
        return new IngestResult(saved, true);
    }

    @Override
    public List<Telemetry> recent(String spaceId, int limit) {
        ResultLimit resultLimit = ResultLimit.of(limit);
        String filter = normalize(spaceId);
        if (filter != null) {
            catalog.getRequiredSpace(filter);
        }
        return repository.findRecent(filter, resultLimit);
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
