package edu.campusnexo.infrastructure.adapter.in.demo;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.horario.SesionDeClase;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The timetable of the whole campus for the demo: the real Software Engineering timetable, unchanged, plus a generated
 * one for every other program of {@code demo/programas-pasto.csv} (undergraduate by day, graduate programs in the
 * evening), so the simulation fills the twin as a full campus would. Generated sessions never overlap a real one, each
 * other in a room, or two classes of one group; a third of the sessions of the health programs are lab practice with
 * half the group. It is a demo timetable, not the official one; the availability view keeps using the real timetable only.
 */
public final class HorarioSimulado {
    static final String RECURSO = "/demo/programas-pasto.csv";
    /** The real timetable is Software Engineering's; its groups are shown with this prefix next to the generated ones. */
    public static final String ISW = "ISW";

    public enum Jornada {
        DIURNA(7, 18), NOCTURNA(18, 22);

        final int desde;
        final int hasta;

        Jornada(int desde, int hasta) {
            this.desde = desde;
            this.hasta = hasta;
        }
    }

    /** One program as the CSV describes it; {@code fuente} says whether its semesters were given or assumed. */
    public record Programa(String codigo, String nombre, String nivel, int semestres, int grupos, int estudiantes,
                           Jornada jornada, int sesiones, int horas, SpaceType espacio, SpaceType practica,
                           List<String> asignaturas, String fuente) {
    }

    /** The timetable and, for the screen, the programs it covers (Software Engineering first) and what was left out. */
    public record Resultado(List<SesionDeClase> sesiones, List<Programa> programas, int sinSalon) {
    }

    private HorarioSimulado() {
    }

    public static List<Programa> programas() {
        try (InputStream in = HorarioSimulado.class.getResourceAsStream(RECURSO)) {
            if (in == null) {
                throw new IllegalStateException("No está " + RECURSO);
            }
            return leer(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<Programa> leer(List<String> lineas) {
        List<Programa> programas = new ArrayList<>();
        boolean cabecera = true;
        for (String linea : lineas) {
            if (linea.isBlank() || linea.startsWith("#")) {
                continue;
            }
            if (cabecera) {
                cabecera = false;
                continue;
            }
            String[] c = linea.split(";", -1);
            if (c.length != 12) {
                throw new IllegalStateException("Fila de programas con " + c.length + " columnas: " + linea);
            }
            String[] espacios = c[9].split("\\+");
            programas.add(new Programa(c[0], c[1], c[2], Integer.parseInt(c[3]), Integer.parseInt(c[4]),
                    Integer.parseInt(c[5]), Jornada.valueOf(c[6]), Integer.parseInt(c[7]), Integer.parseInt(c[8]),
                    SpaceType.valueOf(espacios[0]), espacios.length > 1 ? SpaceType.valueOf(espacios[1]) : null,
                    List.of(c[10].split("\\|")), c[11]));
        }
        return programas;
    }

    /** The real Software Engineering program, as the screen lists it next to the generated ones. */
    static Programa softwareReal(List<SesionDeClase> real) {
        int semestres = real.stream().mapToInt(s -> semestre(s.grupo())).max().orElse(0);
        long grupos = real.stream().map(SesionDeClase::grupo).distinct().count();
        return new Programa(ISW, "Ingeniería de Software", "Pregrado", semestres, (int) grupos, 0, Jornada.DIURNA, 0, 0,
                SpaceType.SALA_COMPUTO, null, List.of(), "horario real 2620");
    }

    private static int semestre(String grupo) {
        String digitos = grupo.replaceAll("\\D", "");
        return digitos.isEmpty() ? 0 : Integer.parseInt(digitos);
    }

    /**
     * The whole campus: the real sessions (their groups prefixed {@value #ISW}) plus the generated ones in the rooms of
     * the catalog where a class can be held. Deterministic: the same inputs give the same timetable.
     */
    public static Resultado campus(List<SesionDeClase> real, List<CampusSpace> catalogo, List<Programa> programas) {
        List<CampusSpace> salones = catalogo.stream().filter(s -> PARA_CLASES.contains(s.type()))
                .sorted(Comparator.comparingInt(CampusSpace::capacity).thenComparing(CampusSpace::spaceId)).toList();
        Ocupacion ocupacion = new Ocupacion();
        List<SesionDeClase> sesiones = new ArrayList<>();
        for (SesionDeClase s : real) {
            SesionDeClase conPrefijo = new SesionDeClase(ISW + s.grupo(), s.estudiantes(), s.curso(), s.dia(), s.inicio(),
                    s.fin(), s.salon(), s.asignado());
            ocupacion.ocupar(conPrefijo);
            sesiones.add(conPrefijo);
        }
        int sinSalon = 0;
        Random rng = new Random(2620);
        for (Programa p : programas) {
            for (int semestre = 1; semestre <= p.semestres(); semestre++) {
                for (int g = 0; g < p.grupos(); g++) {
                    String grupo = p.codigo() + semestre + (char) ('A' + g);
                    // A few students leave each semester; a cohort never drops under a third of its first semester.
                    int estudiantes = Math.max(Math.max(6, p.estudiantes() / 3), p.estudiantes() - 2 * (semestre - 1));
                    for (int k = 0; k < p.sesiones(); k++) {
                        boolean practica = p.practica() != null && k % 3 == 2;
                        SpaceType tipo = practica ? p.practica() : p.espacio();
                        int asistentes = practica && tipo == SpaceType.LABORATORIO ? (estudiantes + 1) / 2 : estudiantes;
                        String curso = p.asignaturas().get((semestre - 1 + k) % p.asignaturas().size())
                                + (practica && tipo == SpaceType.LABORATORIO ? " (práctica)" : "");
                        SesionDeClase sesion = ubicar(grupo, asistentes, curso, p, tipo, k, salones, ocupacion, rng);
                        if (sesion == null) {
                            sinSalon++;
                            continue;
                        }
                        ocupacion.ocupar(sesion);
                        sesiones.add(sesion);
                    }
                }
            }
        }
        List<Programa> todos = new ArrayList<>();
        if (!real.isEmpty()) {
            todos.add(softwareReal(real));
        }
        todos.addAll(programas);
        return new Resultado(List.copyOf(sesiones), List.copyOf(todos), sinSalon);
    }

    /** Spaces where a class can be held; study rooms, the library and the cafeteria stay free for students. */
    static final List<SpaceType> PARA_CLASES = List.of(SpaceType.AULA, SpaceType.SALA_COMPUTO, SpaceType.LABORATORIO,
            SpaceType.AUDITORIO);

    /**
     * First free (day, hour, room) for one session: days in a rotation that spreads the group's week (at most two
     * sessions a day), hours in a shuffled order inside its shift, and, among the free rooms of the type that fit the
     * group, the smallest and then the least used, so the classes spread over the whole campus. When no room of that
     * type is free, a classroom.
     */
    private static SesionDeClase ubicar(String grupo, int asistentes, String curso, Programa p, SpaceType tipo, int k,
                                        List<CampusSpace> salones, Ocupacion ocupacion, Random rng) {
        int ultimoInicio = p.jornada().hasta - p.horas();
        List<Integer> horas = new ArrayList<>();
        for (int h = p.jornada().desde; h <= ultimoInicio; h++) {
            horas.add(h);
        }
        List<SpaceType> tipos = tipo == SpaceType.AULA ? List.of(SpaceType.AULA) : List.of(tipo, SpaceType.AULA);
        int dias = DemoSimulation.DIAS.size();
        int base = Math.floorMod(grupo.hashCode(), dias);
        for (SpaceType t : tipos) {
            for (int intento = 0; intento < dias; intento++) {
                DayOfWeek dia = DemoSimulation.DIAS.get((base + k * 2 + intento) % dias);
                if (ocupacion.sesionesDelGrupo(grupo, dia) >= 2) {
                    continue;
                }
                Collections.shuffle(horas, rng);
                for (int inicio : horas) {
                    int fin = inicio + p.horas();
                    if (!ocupacion.grupoLibre(grupo, dia, inicio, fin)) {
                        continue;
                    }
                    CampusSpace elegido = null;
                    for (CampusSpace salon : salones) {
                        if (salon.type() == t && salon.capacity() >= asistentes
                                && ocupacion.salonLibre(salon.spaceId(), dia, inicio, fin)
                                && (elegido == null || salon.capacity() < elegido.capacity()
                                    || salon.capacity() == elegido.capacity()
                                       && ocupacion.horas(salon.spaceId()) < ocupacion.horas(elegido.spaceId()))) {
                            elegido = salon;
                        }
                    }
                    if (elegido != null) {
                        return new SesionDeClase(grupo, asistentes, curso, dia, inicio, fin, elegido.spaceId(), false);
                    }
                }
            }
        }
        return null;
    }

    /** Which hours of each weekday are taken, per room and per group. */
    private static final class Ocupacion {
        private final Map<String, boolean[][]> salones = new HashMap<>();
        private final Map<String, boolean[][]> grupos = new LinkedHashMap<>();
        private final Map<String, Integer> horasPorSalon = new HashMap<>();

        void ocupar(SesionDeClase s) {
            marcar(salones, s.salon(), s);
            marcar(grupos, s.grupo(), s);
            horasPorSalon.merge(s.salon(), s.fin() - s.inicio(), Integer::sum);
        }

        int horas(String salon) {
            return horasPorSalon.getOrDefault(salon, 0);
        }

        boolean salonLibre(String salon, DayOfWeek dia, int inicio, int fin) {
            return libre(salones.get(salon), dia, inicio, fin);
        }

        boolean grupoLibre(String grupo, DayOfWeek dia, int inicio, int fin) {
            return libre(grupos.get(grupo), dia, inicio, fin);
        }

        int sesionesDelGrupo(String grupo, DayOfWeek dia) {
            boolean[][] horas = grupos.get(grupo);
            if (horas == null) {
                return 0;
            }
            boolean[] delDia = horas[dia.getValue() - 1];
            int bloques = 0;
            for (int h = 0; h < delDia.length; h++) {
                if (delDia[h] && (h == 0 || !delDia[h - 1])) {
                    bloques++;
                }
            }
            return bloques;
        }

        private static void marcar(Map<String, boolean[][]> mapa, String clave, SesionDeClase s) {
            boolean[][] horas = mapa.computeIfAbsent(clave, x -> new boolean[7][24]);
            Arrays.fill(horas[s.dia().getValue() - 1], s.inicio(), s.fin(), true);
        }

        private static boolean libre(boolean[][] horas, DayOfWeek dia, int inicio, int fin) {
            if (horas == null) {
                return true;
            }
            for (int h = inicio; h < fin; h++) {
                if (horas[dia.getValue() - 1][h]) {
                    return false;
                }
            }
            return true;
        }
    }
}
