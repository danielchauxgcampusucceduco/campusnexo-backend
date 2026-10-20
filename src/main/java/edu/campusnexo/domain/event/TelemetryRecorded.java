package edu.campusnexo.domain.event;

import edu.campusnexo.domain.model.CampusSpace;
import edu.campusnexo.domain.model.Telemetry;

public record TelemetryRecorded(Telemetry telemetry, CampusSpace space) {}
