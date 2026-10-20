package edu.campusnexo.infrastructure.adapter.out.observability;

import edu.campusnexo.domain.port.HvacActuatorCommandPort;
import edu.campusnexo.domain.twin.HvacCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Actuator used when MQTT is disabled (local development, CI, cloud demo without hardware). */
@Component
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "false", matchIfMissing = true)
class LoggingHvacActuatorAdapter implements HvacActuatorCommandPort {
    private static final Logger log = LoggerFactory.getLogger(LoggingHvacActuatorAdapter.class);

    @Override
    public void send(HvacCommand command) {
        log.info("Comando HVAC (sin hardware): spaceId={}, mode={}, compressorOn={}, setpoint={} °C, motivo={}",
                command.spaceId(), command.state(), command.compressorOn(), command.setpointC(), command.reason());
    }
}
