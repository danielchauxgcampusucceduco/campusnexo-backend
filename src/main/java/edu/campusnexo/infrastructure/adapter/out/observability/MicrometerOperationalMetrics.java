package edu.campusnexo.infrastructure.adapter.out.observability;

import edu.campusnexo.application.port.out.OperationalMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Micrometer adapter of {@link OperationalMetrics}. Exposed in Prometheus format at
 * {@code /actuator/prometheus} (administrators only):
 * <ul>
 *   <li>{@code campusnexo_readings_discarded_total{source,reason}}</li>
 *   <li>{@code campusnexo_twin_write_conflicts_total}</li>
 *   <li>{@code campusnexo_ai_fallbacks_total{reason}}</li>
 *   <li>{@code campusnexo_ai_circuit_open} (1 while the breaker is open or half open)</li>
 *   <li>{@code campusnexo_frame_latency_seconds} with p50, p95 and p99: capture at the edge to twin published</li>
 * </ul>
 */
@Component
class MicrometerOperationalMetrics implements OperationalMetrics {
    private final MeterRegistry registry;
    private final AtomicInteger circuitOpen = new AtomicInteger();
    private final Timer frameLatency;

    MicrometerOperationalMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("campusnexo.ai.circuit.open", circuitOpen);
        this.frameLatency = Timer.builder("campusnexo.frame.latency")
                .description("Del frame capturado en el borde al gemelo publicado")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    @Override
    public void readingDiscarded(String source, String reason) {
        registry.counter("campusnexo.readings.discarded", "source", source, "reason", reason).increment();
    }

    @Override
    public void twinWriteConflict() {
        registry.counter("campusnexo.twin.write.conflicts").increment();
    }

    @Override
    public void aiFallback(String reason) {
        registry.counter("campusnexo.ai.fallbacks", "reason", reason).increment();
    }

    @Override
    public void aiCircuitState(String state) {
        circuitOpen.set("CERRADO".equals(state.toUpperCase(Locale.ROOT)) ? 0 : 1);
        registry.counter("campusnexo.ai.circuit.transitions", "to", state).increment();
    }

    @Override
    public void frameLatency(Duration latency) {
        // An edge clock ahead of the server gives a negative figure: it says nothing about the delay.
        if (!latency.isNegative()) {
            frameLatency.record(latency);
        }
    }
}
