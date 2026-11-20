package edu.campusnexo.infrastructure.adapter.in.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.horario.SesionDeClase;
import edu.campusnexo.infrastructure.adapter.out.horario.CsvHorarioAcademicoAdapter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HorarioSimuladoTest {
    /** The rooms of the 2025-B plan where a class can be held, with their real capacities, plus the two pilots. */
    private static List<CampusSpace> campusPasto() {
        List<CampusSpace> espacios = new ArrayList<>();
        for (int i = 1; i <= 42; i++) {
            espacios.add(espacio("AULA-" + i, SpaceType.AULA, 36));
        }
        int[] laboratorios = {4, 4, 4, 6, 6, 6, 8, 8, 15, 20, 24, 28, 35};
        for (int i = 0; i < laboratorios.length; i++) {
            espacios.add(espacio("LAB-" + i, SpaceType.LABORATORIO, laboratorios[i]));
        }
        int[] computo = {12, 15, 24, 24, 24, 40};
        for (int i = 0; i < computo.length; i++) {
            espacios.add(espacio("SC-" + i, SpaceType.SALA_COMPUTO, computo[i]));
        }
        espacios.add(espacio("AUD-0", SpaceType.AUDITORIO, 114));
        espacios.add(espacio("AUD-1", SpaceType.AUDITORIO, 176));
        espacios.add(espacio("301-A", SpaceType.SALA_COMPUTO, 30));
        espacios.add(espacio("303-A", SpaceType.AULA, 36));
        espacios.add(espacio("BIBLIOTECA-2A", SpaceType.BIBLIOTECA, 120));
        return espacios;
    }

    private static CampusSpace espacio(String id, SpaceType tipo, int aforo) {
        return new CampusSpace(id, id, tipo, aforo, aforo * 1.5, "BLOQUE-A", "Bloque A", "PASTO", "Pasto", "Pasto");
    }

    @Test
    @DisplayName("Los programas del campus se leen del CSV, con los semestres que dio la universidad")
    void leeLosProgramas() {
        Map<String, HorarioSimulado.Programa> programas = HorarioSimulado.programas().stream()
                .collect(Collectors.toMap(HorarioSimulado.Programa::codigo, p -> p));
        assertEquals(20, programas.size());
        assertEquals(9, programas.get("DER").semestres());
        assertEquals(10, programas.get("ODO").semestres());
        assertEquals("Maestría", programas.get("MDH").nivel());
        assertTrue(programas.values().stream().allMatch(p -> !p.asignaturas().isEmpty()));
    }

    @Test
    @DisplayName("El horario del campus no cruza salones ni grupos, conserva el real y cubre todos los semestres")
    void horarioSinCruces() {
        List<SesionDeClase> real = CsvHorarioAcademicoAdapter.cargar();
        List<HorarioSimulado.Programa> programas = HorarioSimulado.programas();
        HorarioSimulado.Resultado campus = HorarioSimulado.campus(real, campusPasto(), programas);
        List<SesionDeClase> sesiones = campus.sesiones();

        // The real timetable stays as it is, with its groups prefixed.
        assertEquals(real.size(), sesiones.stream().filter(s -> s.grupo().startsWith(HorarioSimulado.ISW)).count());
        // Every semester of every group of every program has its classes.
        for (HorarioSimulado.Programa p : programas) {
            for (int semestre = 1; semestre <= p.semestres(); semestre++) {
                for (int g = 0; g < p.grupos(); g++) {
                    String grupo = p.codigo() + semestre + (char) ('A' + g);
                    assertTrue(sesiones.stream().anyMatch(s -> s.grupo().equals(grupo)), grupo + " sin clases");
                }
            }
        }
        // Almost every generated session found a room.
        int generadas = sesiones.size() - real.size();
        assertTrue(campus.sinSalon() * 50 <= generadas, campus.sinSalon() + " sin salón de " + generadas);

        List<SesionDeClase> generadasLista = sesiones.stream().filter(s -> !s.grupo().startsWith(HorarioSimulado.ISW)).toList();
        for (SesionDeClase a : generadasLista) {
            // Within the campus day; graduate programs in the evening.
            assertTrue(a.inicio() >= 7 && a.fin() <= 22, a.toString());
            if (a.grupo().startsWith("MDH") || a.grupo().startsWith("PED")) {
                assertTrue(a.inicio() >= 18, a.toString());
            }
            for (SesionDeClase b : sesiones) {
                if (a == b || a.dia() != b.dia() || a.fin() <= b.inicio() || b.fin() <= a.inicio()) {
                    continue;
                }
                assertFalse(a.salon().equals(b.salon()), "salón cruzado: " + a + " / " + b);
                assertFalse(a.grupo().equals(b.grupo()), "grupo cruzado: " + a + " / " + b);
            }
        }
        // A room never gets more people than it holds.
        Map<String, Integer> aforo = campusPasto().stream().collect(Collectors.toMap(CampusSpace::spaceId, CampusSpace::capacity));
        assertTrue(generadasLista.stream().allMatch(s -> s.estudiantes() <= aforo.get(s.salon())));
        // Study rooms, the library and the cafeteria are left to the students.
        assertTrue(sesiones.stream().noneMatch(s -> s.salon().startsWith("BIBLIOTECA")));
    }

    @Test
    @DisplayName("La simulación lista los programas con sus grupos y ocupa buena parte del campus")
    void laSimulacionCubreElCampus() {
        List<CampusSpace> catalogo = campusPasto();
        HorarioSimulado.Resultado campus = HorarioSimulado.campus(CsvHorarioAcademicoAdapter.cargar(), catalogo,
                HorarioSimulado.programas());
        DemoSimulation simulacion = new DemoSimulation(DemoSimulation.demoRooms(catalogo, campus.sesiones()),
                campus.sesiones(), campus.programas(), Duration.ofMinutes(20));
        DemoSimulation.Snapshot snapshot = simulacion.snapshot(Instant.parse("2026-11-23T15:00:00Z"));

        assertEquals(21, snapshot.programas().size());
        assertEquals("ISW", snapshot.programas().getFirst().codigo());
        DemoSimulation.ProgramaVista derecho = snapshot.programas().stream().filter(p -> p.codigo().equals("DER")).findFirst()
                .orElseThrow();
        assertEquals(18, derecho.grupos());
        assertTrue(snapshot.salones().size() >= 50, "salones en la demo: " + snapshot.salones().size());
        assertEquals("las carreras del campus Pasto", snapshot.programa());
    }
}
