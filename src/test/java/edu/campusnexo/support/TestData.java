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
import java.util.stream.Stream;

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
        String block = code.endsWith("A") || code.matches("\\dA-.*") ? "BLOQUE-A" : "BLOQUE-B";
        return new CampusSpace(code, "Salón " + code, type, capacity, capacity * 1.8, block,
                block.equals("BLOQUE-A") ? "Bloque A" : "Bloque B", "UCC-PASTO", "Campus Pasto", "Pasto",
                ClimateProfile.SEDE_ANDINA, 13.5, 865, Character.isDigit(code.charAt(0)) ? code.charAt(0) - '0' : 2,
                rows == null ? null : new SeatLayout(rows, columns), computers);
    }

    /** Rooms of the campus plan the twin tests use, plus the two pilot rooms; the library has no climate unit. */
    public static List<CampusSpace> campusRooms() {
        return Stream.of(
                room("420-A", SpaceType.AULA, 30, 5, 6, 1),
                room("421-A", SpaceType.AULA, 30, 5, 6, 1),
                room("422-A", SpaceType.AULA, 30, 5, 6, 1),
                room("423-A", SpaceType.AULA, 18, 3, 6, 1),
                room("503-A", SpaceType.AULA, 30, 5, 6, 1),
                room("402-B", SpaceType.AULA, 45, 5, 9, 1),
                room("414-B", SpaceType.AULA, 40, 5, 8, 1),
                room("309-A", SpaceType.SALA_COMPUTO, 24, 4, 6, 24),
                room("3B-DE-BIOPROCESOS-LABORATORIO", SpaceType.LABORATORIO, 15, 3, 5, 3),
                room("2B-LABORATORIO-MULTIFUNCIONAL", SpaceType.LABORATORIO, 35, 5, 7, 8),
                room("BIBLIOTECA-2B", SpaceType.BIBLIOTECA, 120, null, null, 15).withHvacInstalled(false),
                room("301-A", SpaceType.SALA_COMPUTO, 30, 5, 6, 30),
                room("303-A", SpaceType.AULA, 36, 6, 6, 1)).toList();
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
