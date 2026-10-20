package edu.campusnexo.infrastructure.adapter.out.websocket;

import edu.campusnexo.domain.port.RealtimeDashboardNotifierPort;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.infrastructure.adapter.contract.TwinContracts.TraceResponse;
import edu.campusnexo.infrastructure.adapter.contract.TwinContracts.TwinStateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Pushes twin updates to {@code /topic/twin} and {@code /topic/twin/{spaceId}} and architecture traces to
 * {@code /topic/traces}. A delivery failure is logged: dashboards are best-effort and must never make the
 * telemetry pipeline fail.
 */
@Component
class StompRealtimeDashboardNotifierAdapter implements RealtimeDashboardNotifierPort {
    static final String TWIN_TOPIC = "/topic/twin";
    static final String TRACE_TOPIC = "/topic/traces";
    private static final Logger log = LoggerFactory.getLogger(StompRealtimeDashboardNotifierAdapter.class);

    private final SimpMessagingTemplate messaging;

    StompRealtimeDashboardNotifierAdapter(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @Override
    public void publishTwinUpdate(SpaceTwinState state) {
        TwinStateResponse payload = TwinStateResponse.from(state);
        send(TWIN_TOPIC, payload);
        send(TWIN_TOPIC + "/" + state.spaceId(), payload);
    }

    @Override
    public void publishTrace(ArchitectureTrace trace) {
        send(TRACE_TOPIC, TraceResponse.from(trace));
    }

    private void send(String destination, Object payload) {
        try {
            messaging.convertAndSend(destination, payload);
        } catch (MessagingException failure) {
            log.warn("No se pudo publicar en {}: {}", destination, failure.getMessage());
        }
    }
}
