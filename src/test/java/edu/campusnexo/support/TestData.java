package edu.campusnexo.support;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.climate.HvacState;
import edu.campusnexo.domain.telemetry.Telemetry;
import java.time.Instant;

public final class TestData {
    public static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");

    private TestData() {
    }

    public static CampusSpace andeanClassroom() {
        return new CampusSpace("AULA-301", "Aula 301", SpaceType.AULA, 32, 48, "BLQ-ING-SUR", "Bloque C",
                "SEDE-ANDINA", "Sede Andina", "Pasto", ClimateProfile.SEDE_ANDINA, 14.5, 865);
    }

    public static CampusSpace tropicalClassroom() {
        return new CampusSpace("AULA-MAGNA-204", "Aula Magna 204", SpaceType.AULA, 48, 72, "BLQ-AUL-NORTE",
                "Aulas Magnas", "SEDE-TROPICAL", "Sede Tropical", "Barranquilla", ClimateProfile.SEDE_TROPICAL,
                31.8, 1020);
    }

    public static CampusSpace mildClassroom() {
        return new CampusSpace("AULA-ADM-101", "Aula 101", SpaceType.AULA, 32, 48, "BLQ-ADM-OCC", "Bloque A",
                "SEDE-TEMPLADA", "Sede Templada", "Medellín", ClimateProfile.SEDE_TEMPLADA, 22.8, 940);
    }

    public static CampusSpace library() {
        return new CampusSpace("BIB-PISO-2", "Biblioteca - Piso 2", SpaceType.BIBLIOTECA, 120, 210, "BLQ-BIB-SUR",
                "Biblioteca Central", "SEDE-ANDINA", "Sede Andina", "Pasto", ClimateProfile.SEDE_ANDINA, 14.5, 865);
    }

    public static Telemetry reading(String spaceId, Instant at, int people, double co2, double powerKw) {
        return new Telemetry(spaceId, at, people, 0, people, co2, 22, 55, 300, powerKw);
    }

    public static ClimateContext context(int occupancy, double indoorC, double outsideC, double humidity,
                                         double lux, double co2) {
        return new ClimateContext(occupancy, co2, indoorC, outsideC, humidity, lux, HvacState.initial());
    }

    public static ClimateContext context(int occupancy, double indoorC, HvacMode current, int minutesInMode) {
        return new ClimateContext(occupancy, 600, indoorC, 18, 50, 300, new HvacState(current, minutesInMode));
    }
}
