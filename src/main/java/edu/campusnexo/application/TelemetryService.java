package edu.campusnexo.application;

import edu.campusnexo.application.event.TelemetryEventPublisher;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.TelemetryUseCase;
import edu.campusnexo.domain.event.TelemetryRecorded;
import edu.campusnexo.domain.model.Telemetry;
import edu.campusnexo.domain.port.TelemetryRepository;
import java.util.List;

public class TelemetryService implements TelemetryUseCase {
    private final TelemetryRepository telemetryRepository;
    private final CampusCatalogUseCase spaceCatalog;
    private final TelemetryEventPublisher eventPublisher;
    public TelemetryService(TelemetryRepository telemetryRepository, CampusCatalogUseCase spaceCatalog,
                            TelemetryEventPublisher eventPublisher) {
        this.telemetryRepository = telemetryRepository;
        this.spaceCatalog = spaceCatalog;
        this.eventPublisher = eventPublisher;
    }
    @Override public Telemetry ingest(Telemetry telemetry) {
        var space = spaceCatalog.getRequiredSpace(telemetry.spaceId());
        Telemetry saved = telemetryRepository.save(telemetry);
        eventPublisher.publish(new TelemetryRecorded(saved, space));
        return saved;
    }
    @Override public List<Telemetry> recent(String spaceId, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit debe estar entre 1 y 100");
        if (spaceId != null && !spaceId.isBlank()) spaceCatalog.getRequiredSpace(spaceId);
        return telemetryRepository.findRecent(spaceId, limit);
    }
}
