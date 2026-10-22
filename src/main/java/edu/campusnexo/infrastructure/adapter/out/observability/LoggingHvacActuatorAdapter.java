package edu.campusnexo.infrastructure.adapter.out.observability;

import edu.campusnexo.domain.port.HvacActuatorCommandPort;
import edu.campusnexo.domain.twin.HvacCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Actuator used when MQTT is disabled (local development, CI, cloud demo without hardware). */
@Component
@ConditionalOnExpression("!(${app.mqtt.enabled:false} and '${app.actuators.mode:log}' == 'mqtt')")
class LoggingHvacActuatorAdapter implements HvacActuatorCommandPort {
    private static final Logger log = LoggerFactory.getLogger(LoggingHvacActuatorAdapter.class);

    @Override
    public void send(HvacCommand command) {
        log.info("Comando HVAC (sin hardware): spaceId={}, estrategia={}, modo={}, compressorOn={}, setpoint={} °C, "
                        + "motivo={}", command.spaceId(), command.state(), command.mode(), command.compressorOn(),
                command.setpointC(), command.reason());
    }
}
