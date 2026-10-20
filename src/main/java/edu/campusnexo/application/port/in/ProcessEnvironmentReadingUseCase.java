package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.twin.EnvironmentReading;
import edu.campusnexo.domain.twin.SpaceTwinState;

/** Entry point of the room Arduino: temperature, humidity, light and computer power update the twin. */
public interface ProcessEnvironmentReadingUseCase {
    /**
     * @param inboundAdapter name of the adapter that received the reading (shown in the architecture trace)
     */
    SpaceTwinState processEnvironment(EnvironmentReading reading, String inboundAdapter);
}
