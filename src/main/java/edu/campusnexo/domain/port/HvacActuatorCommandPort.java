package edu.campusnexo.domain.port;

import edu.campusnexo.domain.twin.HvacCommand;

/** Sends HVAC strategy changes to the room controller. */
public interface HvacActuatorCommandPort {
    void send(HvacCommand command);
}
