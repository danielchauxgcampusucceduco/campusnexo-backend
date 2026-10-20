package edu.campusnexo.support;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.catalog.SeatLayout;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.climate.HvacState;
import edu.campusnexo.domain.telemetry.Telemetry;
import java.time.Instant;
import java.util.List;

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

    public static CampusSpace lab() {
        return new CampusSpace("LAB-IA-102", "Laboratorio IA 102", SpaceType.LABORATORIO, 24, 54, "BLQ-ING-SUR",
                "Bloque C", "SEDE-ANDINA", "Sede Andina", "Pasto", ClimateProfile.SEDE_ANDINA, 14.5, 865);
    }

    /** Room of the UCC Pasto campus, e.g. {@code room("203-A", SpaceType.AULA, 30, 5, 6, 1)}. */
    public static CampusSpace room(String code, SpaceType type, int capacity, Integer rows, Integer columns, int computers) {
        String block = code.endsWith("A") ? "BLOQUE-A" : "BLOQUE-B";
        return new CampusSpace(code, "Salón " + code, type, capacity, capacity * 1.8, block,
                block.equals("BLOQUE-A") ? "Bloque A" : "Bloque B", "UCC-PASTO", "Campus Pasto", "Pasto",
                ClimateProfile.SEDE_ANDINA, 13.5, 865, code.charAt(0) - '0',
                rows == null ? null : new SeatLayout(rows, columns), computers);
    }

    /** The rooms used by the defense scenarios, with the same layout as Flyway V11. */
    public static List<CampusSpace> scenarioRooms() {
        return List.of(
                room("202-A", SpaceType.AULA, 30, 5, 6, 1),
                room("203-A", SpaceType.AULA, 30, 5, 6, 1),
                room("204-A", SpaceType.AULA, 30, 5, 6, 1),
                room("302-A", SpaceType.AULA, 30, 5, 6, 1),
                room("304-A", SpaceType.AULA, 30, 5, 6, 1),
                room("401-A", SpaceType.AULA, 35, 5, 7, 1),
                room("503-A", SpaceType.AULA, 30, 5, 6, 1),
                room("103-B", SpaceType.LABORATORIO, 24, 4, 6, 12),
                room("104-B", SpaceType.LABORATORIO, 24, 4, 6, 4),
                room("201-B", SpaceType.BIBLIOTECA, 48, null, null, 8),
                room("302-B", SpaceType.SALA_COMPUTO, 30, 5, 6, 30));
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
