package edu.campusnexo.infrastructure.adapter.in.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.catalog.SeatLayout;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.infrastructure.adapter.in.demo.DemoSimulation.SalaVista;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DemoSimulationTest {
    private static final Duration DAY = Duration.ofMinutes(20);
    private final DemoSimulation simulation = new DemoSimulation(DemoSimulation.demoRooms(campus(), 8), DAY);

    private static CampusSpace lab(String code, String name, int capacity) {
        return new CampusSpace(code, name, SpaceType.LABORATORIO, capacity, capacity * 2.5, "BLOQUE-B", "Bloque B",
                "UCC-PASTO", "Campus Pasto", "Pasto", ClimateProfile.SEDE_ANDINA, 13.5, 865, 2, new SeatLayout(4, 6), 2);
    }

    /** The same mix as the demo set of the real catalog: computer rooms, medicine and engineering labs, classrooms. */
    private static List<CampusSpace> campus() {
        List<CampusSpace> rooms = new ArrayList<>(List.of(
                TestData.room("301-A", SpaceType.SALA_COMPUTO, 30, 5, 6, 30),
                TestData.room("303-A", SpaceType.AULA, 36, 6, 6, 1),
                lab("0B-SIM", "Laboratorio de Simulación Clínica", 28), lab("0B-UCC", "Unidad de Cuidado Crítico", 6),
                lab("1B-MOR", "Laboratorio de Morfología", 8), lab("2B-MIC", "Laboratorio de Microbiología", 4),
                lab("1B-DES", "Área de Destrezas Clínicas", 6), lab("2B-RM", "Laboratorio de Realidad Mixta", 4),
                lab("2B-MUL", "Laboratorio Multifuncional N.º 1", 35), lab("3B-BIO", "Laboratorio de Bioprocesos", 15),
                lab("3B-QUI", "Lab. de Aplicación a la Química", 20), lab("3B-FIS", "Lab. de Aplicación de la Física", 8),
                lab("5B-IND", "Laboratorio de Operaciones · Ing. Industrial", 24)));
        for (String code : List.of("308-A", "309-A", "310-A", "311-A", "312-A", "5B-INF")) {
            rooms.add(TestData.room(code, SpaceType.SALA_COMPUTO, 24, 4, 6, 24));
        }
        IntStream.rangeClosed(201, 210).forEach(n -> rooms.add(TestData.room(n + "-B", SpaceType.AULA, 36, 6, 6, 1)));
        return rooms;
    }

    /** Every five simulated minutes of the first simulated day. */
    private List<SalaVista> oneDay() {
        List<SalaVista> all = new ArrayList<>();
        long step = DAY.toMillis() * 5 / 900;
        for (long t = 0; t < DAY.toMillis(); t += step) {
            all.addAll(simulation.snapshot(Instant.ofEpochMilli(t)).salones());
        }
        return all;
    }

    @Test
    @DisplayName("El set de la demo deja fuera los salones piloto y trae 8 aulas")
    void demoSet() {
        List<String> ids = simulation.salones().stream().map(CampusSpace::spaceId).toList();
        assertTrue(!ids.contains("301-A") && !ids.contains("303-A"));
        assertEquals(8, simulation.salones().stream().filter(s -> s.type() == SpaceType.AULA).count());
    }

    @Test
    @DisplayName("Un solo día simulado muestra todas las situaciones exageradas")
    void oneDayShowsEveryCase() {
        List<String> hallazgos = oneDay().stream().flatMap(s -> s.ahora().hallazgos().stream()).toList();
        for (String caso : List.of("Reservado para", "En mantenimiento", "Vacío con las luces", "Vacío con el",
                "Vacío con equipos", "A oscuras", "Sobrecupo", "Cámara normal sin señal")) {
            assertTrue(hallazgos.stream().anyMatch(h -> h.startsWith(caso)), "falta: " + caso);
        }
    }

    @Test
    @DisplayName("A oscuras la cámara normal pierde personas; la infrarroja no")
    void infraredSeesInTheDark() {
        List<SalaVista> dark = oneDay().stream()
                .filter(s -> s.ahora().personas() > 5 && !s.ahora().luces() && s.ahora().camaraNormalEnLinea()).toList();
        assertTrue(!dark.isEmpty());
        dark.forEach(s -> {
            assertTrue(s.ahora().personasCamaraNormal() < s.ahora().personasInfrarroja());
            assertEquals(s.ahora().personas(), s.ahora().personasInfrarroja());
        });
        // In the dark the normal camera still makes out about a third of the people, not none.
        int seen = dark.stream().mapToInt(s -> s.ahora().personasCamaraNormal()).sum();
        int present = dark.stream().mapToInt(s -> s.ahora().personas()).sum();
        assertTrue(seen > present * 0.15 && seen < present * 0.5, seen + " de " + present);
    }

    @Test
    @DisplayName("Sin cámara normal no hay conteo normal, y no se juzga ninguna pantalla")
    void normalCameraDown() {
        List<SalaVista> down = oneDay().stream().filter(s -> !s.ahora().camaraNormalEnLinea()).toList();
        assertTrue(!down.isEmpty());
        down.forEach(s -> {
            assertEquals(null, s.ahora().personasCamaraNormal());
            assertTrue(s.ahora().dispositivos().stream().allMatch(d -> d.camaraNormal() == null));
        });
    }

    @Test
    @DisplayName("Neveras, incubadoras y biorreactores siempre encendidos y calientes; un equipo recién apagado sale tibio")
    void thermalReadings() {
        List<SalaVista> day = oneDay();
        day.stream().flatMap(s -> s.ahora().dispositivos().stream()).filter(DemoSimulation.DispositivoVista::siempreEncendido)
                .forEach(d -> {
                    assertEquals(d.cantidad(), d.encendidos());
                    assertEquals("CALIENTE", d.termico());
                });
        assertTrue(day.stream().flatMap(s -> s.ahora().dispositivos().stream())
                .anyMatch(d -> d.encendidos() == 0 && d.termico().equals("TIBIO")));
        // Screens the normal camera can judge, devices without a screen only the infrared camera can.
        day.stream().flatMap(s -> s.ahora().dispositivos().stream()).filter(d -> !d.pantalla())
                .forEach(d -> assertEquals(null, d.camaraNormal()));
    }

    @Test
    @DisplayName("Mismo instante, mismo campus; el día siguiente trae otro horario")
    void deterministicPerDay() {
        Instant now = Instant.parse("2026-10-27T15:00:00Z");
        assertEquals(simulation.snapshot(now), simulation.snapshot(now));
        assertTrue(!simulation.snapshot(now).salones().stream().map(SalaVista::horario).toList()
                .equals(simulation.snapshot(now.plus(DAY)).salones().stream().map(SalaVista::horario).toList()));
    }
}
