package edu.campusnexo.application;

import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.application.event.TelemetryEventPublisher;
import edu.campusnexo.domain.event.TelemetryRecorded;
import edu.campusnexo.domain.model.CampusSpace;
import edu.campusnexo.domain.model.Telemetry;
import edu.campusnexo.domain.port.TelemetryRepository;
import java.time.Instant;
import java.util.List;

public class TelemetryService {
    private final TelemetryRepository telemetryRepository;
    private final SpaceCatalogService spaceCatalog;
    private final TelemetryEventPublisher eventPublisher;
    public TelemetryService(TelemetryRepository telemetryRepository, SpaceCatalogService spaceCatalog,
                            TelemetryEventPublisher eventPublisher) {
        this.telemetryRepository = telemetryRepository;
        this.spaceCatalog = spaceCatalog;
        this.eventPublisher = eventPublisher;
    }
    public Telemetry ingest(Telemetry telemetry) {
        CampusSpace space = spaceCatalog.getRequired(telemetry.spaceId());
        Telemetry saved = telemetryRepository.save(telemetry);
        eventPublisher.publish(new TelemetryRecorded(saved, space));
        return saved;
    }
    public List<Telemetry> recent(String spaceId, int limit) {
        if (spaceId != null && !spaceId.isBlank()) spaceCatalog.getRequired(spaceId);
        return telemetryRepository.findRecent(spaceId, limit);
    }
    public List<CampusSpace> spaces() { return spaceCatalog.list(); }
    public CampusSpace space(String spaceId) { return spaceCatalog.getRequired(spaceId); }
    public long readingsSince(Instant from) { return telemetryRepository.countSince(from); }
}
