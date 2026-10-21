package edu.campusnexo.infrastructure.adapter.out.observability;

import edu.campusnexo.domain.port.RoomEquipmentCommandPort;
import edu.campusnexo.domain.twin.EquipmentCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Equipment actuator used when MQTT is disabled (local development, CI, cloud demo without hardware). */
@Component
@ConditionalOnExpression("!(${app.mqtt.enabled:false} and '${app.actuators.mode:log}' == 'mqtt')")
class LoggingEquipmentCommandAdapter implements RoomEquipmentCommandPort {
    private static final Logger log = LoggerFactory.getLogger(LoggingEquipmentCommandAdapter.class);

    @Override
    public void send(EquipmentCommand command) {
        log.info("Orden de apagado (sin hardware): spaceId={}, apagarLuces={}, suspenderEquipos={}, motivo={}",
                command.spaceId(), command.lightsOff(), command.suspendDevices(), command.reason());
    }
}
