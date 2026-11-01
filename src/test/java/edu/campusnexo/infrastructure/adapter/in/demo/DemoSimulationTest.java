package edu.campusnexo.infrastructure.adapter.in.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.vision.DetectedObjectClass;
import edu.campusnexo.domain.vision.NormalizedPoint;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import edu.campusnexo.domain.vision.TrackedDetection;
import edu.campusnexo.infrastructure.adapter.in.demo.DemoSimulation.SalaVista;
import edu.campusnexo.infrastructure.adapter.in.demo.DemoSimulation.Sesion;
import edu.campusnexo.infrastructure.adapter.in.demo.DemoSimulation.Uso;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DemoSimulationTest {
    private static final Duration DAY = Duration.ofMinutes(20);
    private static final List<Sesion> HORARIO = DemoSimulation.horario();
    private static final Set<String> COMPUTO = Set.of("301-A", "308-A", "309-A", "310-A", "5A-AULA-DE-INFORMATICA");
    private final DemoSimulation simulation = new DemoSimulation(DemoSimulation.demoRooms(catalog(), HORARIO), HORARIO, DAY);

    /** Every room the timetable uses, as the catalog has it, plus rooms the demo must leave alone. */
    private static List<CampusSpace> catalog() {
        List<CampusSpace> rooms = new ArrayList<>();
        for (String id : HORARIO.stream().map(Sesion::salon).collect(Collectors.toCollection(java.util.TreeSet::new))) {
            rooms.add(COMPUTO.contains(id) ? TestData.room(id, SpaceType.SALA_COMPUTO, 24, 4, 6, 24)
                    : TestData.room(id, SpaceType.AULA, 36, 6, 6, 1));
        }
        rooms.add(TestData.room("207-B", SpaceType.AULA, 36, 6, 6, 1));
        rooms.add(TestData.room("BIBLIOTECA-2A", SpaceType.BIBLIOTECA, 120, null, null, 15));
        return rooms;
    }

    /** The instant of {@code weekday} (0 Monday .. 4 Friday) at {@code minuto} of the day, in the first loop. */
    private static Instant at(int weekday, double minuto) {
        long dayMs = DAY.toMillis();
        return Instant.ofEpochMilli(weekday * dayMs + (long) ((minuto - DemoSimulation.DAY_START)
                / (DemoSimulation.DAY_END - DemoSimulation.DAY_START) * dayMs));
    }

    /** Every five simulated minutes of the five weekdays. */
    private List<SalaVista> week() {
        List<SalaVista> all = new ArrayList<>();
        for (int weekday = 0; weekday < 5; weekday++) {
            for (int minuto = DemoSimulation.DAY_START; minuto < DemoSimulation.DAY_END; minuto += 5) {
                all.addAll(simulation.snapshot(at(weekday, minuto)).salones());
            }
        }
        return all;
    }

    @Test
    @DisplayName("El horario 2620 de Ingeniería de Software trae 101 clases, sin dos grupos a la vez en un salón")
    void timetable() {
        assertEquals(101, HORARIO.size());
        assertTrue(HORARIO.stream().allMatch(s -> s.inicio() >= 7 && s.fin() <= 22 && s.inicio() < s.fin()));
        assertTrue(HORARIO.stream().allMatch(s -> DemoSimulation.DIAS.contains(s.dia())));
        for (Sesion a : HORARIO) {
            for (Sesion b : HORARIO) {
                if (a != b && a.salon().equals(b.salon()) && a.dia().equals(b.dia())) {
                    assertTrue(a.fin() <= b.inicio() || b.fin() <= a.inicio(), a + " cruza con " + b);
                }
            }
        }
        assertTrue(HORARIO.stream().anyMatch(Sesion::asignado), "las clases sin salón reciben uno");
    }

    @Test
    @DisplayName("La demo emula solo los salones del horario")
    void onlyTimetableRooms() {
        Set<String> rooms = simulation.salones().stream().map(CampusSpace::spaceId).collect(Collectors.toSet());
        assertEquals(HORARIO.stream().map(Sesion::salon).collect(Collectors.toSet()), rooms);
        assertFalse(rooms.contains("207-B"));
        assertEquals(SpaceType.SALA_COMPUTO, simulation.salones().getFirst().type(), "salas de cómputo primero");
    }

    @Test
    @DisplayName("Cada clase del horario aparece en su salón, su día y sus franjas; fuera de ellas el salón está libre")
    void everySessionInItsSlots() {
        for (int weekday = 0; weekday < 5; weekday++) {
            String dia = DemoSimulation.DIAS.get(weekday);
            var snapshot = simulation.snapshot(at(weekday, 600));
            assertEquals(List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes").get(weekday), snapshot.diaSemana());
            for (SalaVista sala : snapshot.salones()) {
                for (int f = 0; f < DemoSimulation.FRANJAS.size(); f++) {
                    int hora = 7 + f;
                    var bloque = sala.horario().get(f);
                    var sesion = HORARIO.stream().filter(s -> s.salon().equals(sala.spaceId()) && s.dia().equals(dia)
                            && s.inicio() <= hora && hora < s.fin()).findFirst();
                    if (sesion.isPresent()) {
                        assertTrue(bloque.uso() != Uso.LIBRE, sala.spaceId() + " " + dia + " " + hora);
                        assertEquals(sesion.get().curso() + " · " + sesion.get().grupo(), bloque.curso());
                    } else {
                        assertEquals(Uso.LIBRE, bloque.uso(), sala.spaceId() + " " + dia + " " + hora);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("En clase están los estudiantes y el docente; fuera del horario nadie")
    void peopleFollowTheTimetable() {
        int checked = 0;
        for (Sesion s : HORARIO) {
            int weekday = DemoSimulation.DIAS.indexOf(s.dia());
            SalaVista mid = simulation.snapshot(at(weekday, (s.inicio() * 60 + s.fin() * 60) / 2.0)).salones().stream()
                    .filter(v -> v.spaceId().equals(s.salon())).findFirst().orElseThrow();
            if (mid.ahora().uso() == Uso.RESERVA_SIN_USO) {
                assertEquals(0, mid.ahora().personas());
                continue;
            }
            checked++;
            long students = mid.ahora().personasVistas().stream().filter(p -> !p.docente()).count();
            assertTrue(mid.ahora().docente(), "el docente también ocupa el salón: " + s);
            assertEquals(mid.ahora().personas(), students + 1);
            assertTrue(students >= Math.round(s.estudiantes() * 0.7) && students <= s.estudiantes(), s + ": " + students);
            if (COMPUTO.contains(s.salon())) {
                int pcs = mid.ahora().dispositivos().stream().filter(d -> d.categoria().equals("COMPUTO"))
                        .mapToInt(DemoSimulation.DispositivoVista::encendidos).sum();
                assertEquals(Math.min(24, students), pcs, "un PC por estudiante, no por el docente");
            }
        }
        assertTrue(checked > 90);
        week().stream().filter(v -> v.ahora().uso() == Uso.LIBRE)
                .forEach(v -> assertEquals(0, v.ahora().personas(), v.spaceId() + " libre sin personas"));
    }

    @Test
    @DisplayName("Alrededor del horario: olvidos tras la clase, clases a oscuras y cámara normal caída")
    void whatSurroundsTheTimetable() {
        List<String> hallazgos = week().stream().flatMap(s -> s.ahora().hallazgos().stream()).toList();
        for (String caso : List.of("Vacío con las luces", "Vacío con el", "Vacío con equipos", "A oscuras",
                "Cámara normal sin señal")) {
            assertTrue(hallazgos.stream().anyMatch(h -> h.startsWith(caso)), "falta: " + caso);
        }
        assertFalse(hallazgos.stream().anyMatch(h -> h.contains("mantenimiento")), "la demo no inventa mantenimientos");
    }

    @Test
    @DisplayName("A oscuras la cámara normal ve cerca de un tercio; la infrarroja a todos")
    void infraredSeesInTheDark() {
        List<SalaVista> dark = week().stream()
                .filter(s -> s.ahora().personas() > 5 && !s.ahora().luces() && s.ahora().camaraNormalEnLinea()).toList();
        assertFalse(dark.isEmpty());
        int seen = dark.stream().mapToInt(s -> s.ahora().personasCamaraNormal()).sum();
        int present = dark.stream().mapToInt(s -> s.ahora().personas()).sum();
        assertTrue(seen > present * 0.15 && seen < present * 0.5, seen + " de " + present);
        dark.forEach(s -> assertEquals(s.ahora().personas(), s.ahora().personasInfrarroja()));
    }

    @Test
    @DisplayName("Sin cámara normal no hay conteo normal; la infrarroja muestra equipos tibios recién apagados")
    void camerasAndHeat() {
        List<SalaVista> week = week();
        week.stream().filter(s -> !s.ahora().camaraNormalEnLinea()).forEach(s -> {
            assertEquals(null, s.ahora().personasCamaraNormal());
            assertTrue(s.ahora().dispositivos().stream().allMatch(d -> d.camaraNormal() == null));
        });
        assertTrue(week.stream().flatMap(s -> s.ahora().dispositivos().stream())
                .anyMatch(d -> d.encendidos() == 0 && d.termico().equals("TIBIO")));
    }

    @Test
    @DisplayName("Mismo instante, mismo campus; el día siguiente es el siguiente día hábil")
    void deterministicWeek() {
        Instant now = Instant.parse("2026-11-02T15:00:00Z");
        assertEquals(simulation.snapshot(now), simulation.snapshot(now));
        List<String> order = new ArrayList<>();
        for (int d = 0; d < 7; d++) {
            order.add(simulation.snapshot(now.plus(DAY.multipliedBy(d))).diaSemana());
        }
        int monday = order.indexOf("Lunes");
        assertEquals(List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes"), order.subList(monday, monday + 5));
    }

    /** The five silent-study tables of the corner camera, as in Flyway V13. */
    private static List<StudyZoneRoi> cornerTables() {
        double[][] boxes = {{0.06, 0.08, 0.30, 0.28}, {0.06, 0.33, 0.30, 0.53}, {0.06, 0.58, 0.30, 0.78},
                {0.40, 0.72, 0.62, 0.94}, {0.68, 0.72, 0.90, 0.94}};
        List<StudyZoneRoi> rois = new ArrayList<>();
        for (int i = 0; i < boxes.length; i++) {
            double[] b = boxes[i];
            rois.add(new StudyZoneRoi("BIB2A-S0" + (i + 1), DemoSimulation.BIBLIOTECA_ESQUINA, "Silencio " + (i + 1),
                    List.of(new NormalizedPoint(b[0], b[1]), new NormalizedPoint(b[2], b[1]),
                            new NormalizedPoint(b[2], b[3]), new NormalizedPoint(b[0], b[3]))));
        }
        return rois;
    }

    @Test
    @DisplayName("Las cámaras de la biblioteca ven estudiantes y pertenencias dentro de sus mesas, y cada mesa cambia con el tiempo")
    void libraryTables() {
        List<StudyZoneRoi> rois = cornerTables();
        Set<String> states = new HashSet<>();
        Instant start = Instant.parse("2026-11-01T14:00:00Z");
        for (int hour = 0; hour < 12; hour++) {
            List<TrackedDetection> seen = DemoSimulation.mesas(rois, start.plus(Duration.ofHours(hour)));
            seen.forEach(d -> assertTrue(rois.stream().anyMatch(r -> r.containsPoint(d.box().center())),
                    "cada detección cae dentro de una mesa"));
            for (StudyZoneRoi roi : rois) {
                List<TrackedDetection> inside = seen.stream().filter(d -> roi.containsPoint(d.box().center())).toList();
                boolean people = inside.stream().anyMatch(d -> d.objectClass() == DetectedObjectClass.PERSON);
                states.add(people ? "ocupada" : inside.isEmpty() ? "libre" : "pertenencias");
            }
        }
        assertEquals(Set.of("ocupada", "libre", "pertenencias"), states);
        Instant t = start.plus(Duration.ofMinutes(3));
        assertEquals(DemoSimulation.mesas(rois, t), DemoSimulation.mesas(rois, t.plusSeconds(1)),
                "una mesa no cambia de un frame al siguiente");
    }
}
