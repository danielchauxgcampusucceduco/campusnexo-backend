package edu.campusnexo.infrastructure.adapter.out.history;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import edu.campusnexo.application.port.out.TwinChangeNotifierPort;
import edu.campusnexo.application.port.out.TwinHistoryPort;
import edu.campusnexo.domain.escenario.ReconocedorDeEscenarios;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.twin.KalmanMeasurement.MeasurementSource;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MongoDB follower of the twin: every stored change becomes a flat document in {@code historial_gemelo} and
 * every architecture trace one in {@code trazas} (kept {@value #TRACE_DAYS} days). It writes after PostgreSQL
 * committed, as a best effort: if MongoDB is down the twin keeps working, the failure is logged and that
 * entry is missing from the history.
 */
public final class MongoTwinHistoryAdapter implements TwinChangeNotifierPort, TwinHistoryPort {
    static final String HISTORY = "historial_gemelo";
    static final String TRACES = "trazas";
    static final int TRACE_DAYS = 30;
    private static final Logger log = LoggerFactory.getLogger(MongoTwinHistoryAdapter.class);
    private static final ReconocedorDeEscenarios ESCENARIOS = ReconocedorDeEscenarios.estandar();

    private final MongoCollection<Document> history;
    private final MongoCollection<Document> traces;

    public MongoTwinHistoryAdapter(MongoDatabase database) {
        this.history = database.getCollection(HISTORY);
        this.traces = database.getCollection(TRACES);
    }

    /** Indexes for the per-room time queries and the expiry of old traces; idempotent. */
    public void createIndexes() {
        history.createIndex(Indexes.ascending("spaceId", "at"));
        traces.createIndex(Indexes.ascending("startedAt"),
                new IndexOptions().expireAfter((long) TRACE_DAYS, TimeUnit.DAYS));
    }

    @Override
    public void publishTwinUpdate(SpaceTwinState state) {
        Document entry = new Document("spaceId", state.spaceId())
                .append("at", Date.from(state.updatedAt()))
                .append("status", state.status().name())
                .append("condition", state.condition().name())
                .append("hvacState", state.hvacState().name())
                .append("people", state.occupancy().people())
                .append("estimate", state.occupancy().estimate())
                .append("stdDev", state.occupancy().standardDeviation())
                .append("teachers", state.presence().teachers())
                .append("students", state.presence().students())
                .append("cameraPeople", cameraPeople(state))
                .append("co2Ppm", state.co2Ppm())
                .append("indoorTemperatureC", state.indoorTemperatureC())
                .append("lightsOn", state.equipment().lightsOn())
                .append("devicesOn", state.equipment().devicesOn())
                .append("cameraOnline", state.cameraOnline())
                .append("emulated", state.sensing().emulated())
                .append("energySavedKwh", state.energySavedKwh())
                .append("escenarios", ESCENARIOS.reconocer(state, state.updatedAt()).stream().map(Enum::name).toList());
        insert(history, entry);
    }

    @Override
    public void publishTrace(ArchitectureTrace trace) {
        List<Document> steps = trace.steps().stream()
                .map(step -> new Document("layer", step.layer().name()).append("component", step.component())
                        .append("detail", step.detail()).append("offsetMicros", step.offsetMicros()))
                .toList();
        insert(traces, new Document("_id", trace.traceId().toString())
                .append("trigger", trace.trigger())
                .append("spaceId", trace.spaceId())
                .append("startedAt", Date.from(trace.startedAt()))
                .append("steps", steps));
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<OccupancySample> occupancy(String spaceId, Instant from, Instant to) {
        List<OccupancySample> samples = new ArrayList<>();
        history.find(Filters.and(Filters.eq("spaceId", spaceId), Filters.gte("at", Date.from(from)),
                        Filters.lt("at", Date.from(to))))
                .sort(Sorts.ascending("at"))
                .limit(MAX_SAMPLES)
                .forEach(doc -> samples.add(new OccupancySample(doc.getDate("at").toInstant(), spaceId,
                        doc.getInteger("people", 0), number(doc, "estimate"), number(doc, "stdDev"),
                        doc.getInteger("teachers", 0), doc.getInteger("students", 0), doc.getInteger("cameraPeople"),
                        number(doc, "co2Ppm"), doc.getBoolean("cameraOnline", false), doc.getBoolean("emulated", false),
                        doc.getList("escenarios", String.class, List.of()))));
        return samples;
    }

    /** Upper bound of one query: a month of one room at one change per minute. */
    static final int MAX_SAMPLES = (int) Duration.ofDays(31).toMinutes();

    /** The camera's own count as the Kalman filter received it, or {@code null} if the camera did not report. */
    private static Integer cameraPeople(SpaceTwinState state) {
        return state.lastKalmanStep() == null ? null : state.lastKalmanStep().measurements().stream()
                .filter(m -> m.source() == MeasurementSource.CAMARA_YOLO)
                .map(m -> (int) Math.round(m.value()))
                .findFirst().orElse(null);
    }

    private static double number(Document doc, String field) {
        Object value = doc.get(field);
        return value instanceof Number n ? n.doubleValue() : 0;
    }

    private static void insert(MongoCollection<Document> collection, Document document) {
        try {
            collection.insertOne(document);
        } catch (MongoException failure) {
            log.warn("No se pudo guardar en {} de MongoDB: {}", collection.getNamespace().getCollectionName(),
                    failure.getMessage());
        }
    }
}
