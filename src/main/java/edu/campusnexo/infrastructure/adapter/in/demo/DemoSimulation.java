package edu.campusnexo.infrastructure.adapter.in.demo;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SeatLayout;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.horario.SesionDeClase;
import edu.campusnexo.domain.vision.BoundingBox;
import edu.campusnexo.domain.vision.DetectedObjectClass;
import edu.campusnexo.domain.vision.NormalizedPoint;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import edu.campusnexo.domain.vision.TrackedDetection;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Compressed academic week for the system demo, as a pure function of the wall clock: each loop is one weekday of the
 * academic timetable (Monday to Friday, 07:00 to 22:00, in one-hour slots), and only the rooms that timetable uses take
 * part. Every class happens in its room with its course and group: the enrolled students arrive (most of them, as in a
 * real class) and the teacher with them. Around the timetable the demo plays what the camera has to cope with: now and
 * then a class nobody attends (the room is free though booked) and a camera that loses its signal for a while. Every
 * instant says where the people are, as the room's camera sees them projected to the floor.
 */
public final class DemoSimulation {
    static final int DAY_START = 7 * 60;
    static final int DAY_END = 22 * 60;
    public static final List<Franja> FRANJAS = IntStream.range(7, 22)
            .mapToObj(h -> new Franja(String.format(Locale.ROOT, "%02d:00–%02d:00", h, h + 1), h * 60, (h + 1) * 60))
            .toList();
    public static final List<DayOfWeek> DIAS = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
    private static final List<String> NOMBRES_DIA = List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes");
    public static final String PROGRAMA = "Ingeniería de Software · horario 2620";

    /** RESERVA_SIN_USO: a class of the timetable that nobody attended, so the room is free though scheduled. */
    public enum Uso { CLASE, RESERVA_SIN_USO, LIBRE }

    public record Franja(String etiqueta, int desde, int hasta) {
        boolean contiene(double minuto) {
            return minuto >= desde && minuto < hasta;
        }
    }

    private final List<CampusSpace> salones;
    private final List<SesionDeClase> horario;
    private final Duration dayLength;

    public DemoSimulation(List<CampusSpace> salones, List<SesionDeClase> horario, Duration dayLength) {
        this.salones = List.copyOf(salones);
        this.horario = List.copyOf(horario);
        this.dayLength = dayLength;
    }

    /** The demo set: the rooms of the timetable that the catalog has, computer rooms first. */
    public static List<CampusSpace> demoRooms(List<CampusSpace> catalog, List<SesionDeClase> horario) {
        Set<String> usados = horario.stream().map(SesionDeClase::salon).collect(Collectors.toSet());
        return catalog.stream().filter(s -> usados.contains(s.spaceId()))
                .sorted(Comparator.comparing((CampusSpace s) -> s.type() == SpaceType.SALA_COMPUTO ? 0 : 1)
                        .thenComparing(CampusSpace::spaceId))
                .toList();
    }

    public List<CampusSpace> salones() {
        return salones;
    }

    /**
     * A generator seeded from the parts. {@code new Random(seed)} with close seeds gives correlated first draws (the
     * same plan on consecutive days), so the seed is mixed first.
     */
    private static Random rng(Object... parts) {
        return new Random(new SplittableRandom(Objects.hash(parts)).nextLong());
    }

    // ---- The library ------------------------------------------------------------------------------------------------

    /** Reading room of the floor-2 library and its silent-study zone, watched by a corner camera (Flyway V13, V14). */
    public static final String BIBLIOTECA = "BIBLIOTECA-2A";
    public static final String BIBLIOTECA_ESQUINA = "BIBLIOTECA-2A-SILENCIO";
    static final Duration VENTANA_MESA = Duration.ofMinutes(20);

    /**
     * What a library camera sees at its tables. Unlike the classrooms it runs on real time: each table keeps its
     * state for {@link #VENTANA_MESA}, longer than the 15 minutes the twin waits before releasing a table held only by
     * belongings, so the demo shows the whole anti-hoarding cycle. A table has students (with a laptop or a book),
     * belongings without anyone, or nothing; the tables change at different moments.
     */
    public static List<TrackedDetection> mesas(List<StudyZoneRoi> rois, Instant now) {
        List<TrackedDetection> vistas = new ArrayList<>();
        long track = 1;
        long ventanaS = VENTANA_MESA.toSeconds();
        for (StudyZoneRoi roi : rois) {
            long desfase = Math.floorMod(roi.zoneId().hashCode(), ventanaS);
            Random rng = rng(roi.zoneId(), Math.floorDiv(now.getEpochSecond() + desfase, ventanaS));
            double r = rng.nextDouble();
            NormalizedPoint centro = centro(roi.polygon());
            if (r < 0.45) {
                int estudiantes = 1 + rng.nextInt(3);
                for (int k = 0; k < estudiantes; k++) {
                    vistas.add(objeto(track++, DetectedObjectClass.PERSON, centro, (k - (estudiantes - 1) / 2.0) * 0.05, 0));
                }
                vistas.add(objeto(track++, rng.nextBoolean() ? DetectedObjectClass.LAPTOP : DetectedObjectClass.BOOK,
                        centro, 0, 0.04));
            } else if (r < 0.75) {
                vistas.add(objeto(track++, DetectedObjectClass.BACKPACK, centro, -0.03, 0));
                vistas.add(objeto(track++, DetectedObjectClass.BOOK, centro, 0.03, 0.02));
            }
        }
        return vistas;
    }

    private static NormalizedPoint centro(List<NormalizedPoint> poligono) {
        return new NormalizedPoint(poligono.stream().mapToDouble(NormalizedPoint::x).average().orElse(0.5),
                poligono.stream().mapToDouble(NormalizedPoint::y).average().orElse(0.5));
    }

    private static TrackedDetection objeto(long track, DetectedObjectClass clase, NormalizedPoint centro, double dx, double dy) {
        double x = centro.x() + dx;
        double y = centro.y() + dy;
        return new TrackedDetection(track, clase, 0.9, new BoundingBox(Math.max(0, x - 0.015), Math.max(0, y - 0.02),
                Math.min(1, x + 0.015), Math.min(1, y + 0.02)));
    }

    // ---- Output -------------------------------------------------------------------------------------------------

    /** {@code dia} counts the loops of the simulated week; {@code diaSemana} names the weekday it plays. */
    public record Snapshot(Instant generadoEn, int dia, String diaSemana, String programa, String hora,
                           int minutoDelDia, int franjaActual, double minutosSimuladosPorMinuto, List<Franja> franjas,
                           List<SalaVista> salones) {
    }

    public record SalaVista(String spaceId, String nombre, SpaceType tipo, String edificio, int piso, int aforo,
                            int filas, int columnas, List<Bloque> horario, Ahora ahora) {
    }

    /** The plan of one slot; {@code detalle} names what goes wrong in it, if anything. */
    public record Bloque(Uso uso, String curso, String detalle) {
    }

    /**
     * The room now. {@code camaraEnLinea} false: the camera lost its signal and sends frames without detections, so the
     * twin keeps its last estimate. {@code libreHasta}: until which time of the day the room stays free (the start of
     * its next class, or the end of the day), {@code null} while a class is under way.
     */
    public record Ahora(Uso uso, String curso, int personas, boolean docente, boolean camaraEnLinea, String libreHasta,
                        List<PersonaVista> personasVistas, List<String> hallazgos) {
    }

    public record PersonaVista(double x, double y, boolean docente) {
    }

    public Snapshot snapshot(Instant now) {
        long dayMs = dayLength.toMillis();
        int dia = (int) Math.floorDiv(now.toEpochMilli(), dayMs);
        double minuto = DAY_START + (double) Math.floorMod(now.toEpochMilli(), dayMs) / dayMs * (DAY_END - DAY_START);
        List<SalaVista> vistas = salones.stream().map(s -> vista(s, dia, minuto)).toList();
        int entero = (int) minuto;
        return new Snapshot(now, dia, NOMBRES_DIA.get(Math.floorMod(dia, DIAS.size())), PROGRAMA, hora(entero), entero,
                franjaDe(minuto), (DAY_END - DAY_START) / (dayMs / 60_000.0), FRANJAS, vistas);
    }

    static int franjaDe(double minuto) {
        for (int i = 0; i < FRANJAS.size(); i++) {
            if (FRANJAS.get(i).contiene(minuto)) {
                return i;
            }
        }
        return FRANJAS.size() - 1;
    }

    private static String hora(int minuto) {
        return String.format(Locale.ROOT, "%02d:%02d", minuto / 60, minuto % 60);
    }

    // ---- The day plan of a room ---------------------------------------------------------------------------------

    record Sala(CampusSpace space, int filas, int columnas) {
    }

    /** The classes of the room on that weekday and, per class, who attends and whether the camera drops in it. */
    record Plan(List<SesionDeClase> sesiones, List<Integer> asistentes, List<Boolean> sinAsistencia,
                List<Boolean> camaraCaida) {
        int sesionEn(double minuto) {
            for (int i = 0; i < sesiones.size(); i++) {
                if (minuto >= sesiones.get(i).desde() && minuto < sesiones.get(i).hasta()) {
                    return i;
                }
            }
            return -1;
        }

        boolean enCurso(int i) {
            return i >= 0 && !sinAsistencia.get(i);
        }

        /** Start of the next class after that minute, or the end of the day. */
        int siguienteDesde(double minuto) {
            return sesiones.stream().mapToInt(SesionDeClase::desde).filter(desde -> desde > minuto).min().orElse(DAY_END);
        }
    }

    static Sala sala(CampusSpace space) {
        SeatLayout layout = space.seatLayout();
        int columnas = layout != null ? layout.columns() : (int) Math.ceil(Math.sqrt(Math.max(1, space.capacity())));
        int filas = layout != null ? layout.rows() : (int) Math.ceil(Math.max(1, space.capacity()) / (double) columnas);
        return new Sala(space, filas, columnas);
    }

    Plan plan(Sala sala, int dia) {
        DayOfWeek weekday = DIAS.get(Math.floorMod(dia, DIAS.size()));
        List<SesionDeClase> sesiones = horario.stream()
                .filter(s -> s.salon().equals(sala.space().spaceId()) && s.dia() == weekday)
                .sorted(Comparator.comparingInt(SesionDeClase::inicio)).toList();
        List<Integer> asistentes = new ArrayList<>();
        List<Boolean> sinAsistencia = new ArrayList<>();
        List<Boolean> camaraCaida = new ArrayList<>();
        for (SesionDeClase sesion : sesiones) {
            Random rng = rng(sala.space().spaceId(), dia, sesion.inicio());
            // Most of the group comes; once in a while a class of the timetable has nobody.
            asistentes.add(Math.max(3, (int) Math.round(sesion.estudiantes() * (0.72 + rng.nextDouble() * 0.23))));
            sinAsistencia.add(rng.nextDouble() < 0.08);
            camaraCaida.add(rng.nextDouble() < 0.12);
        }
        return new Plan(sesiones, asistentes, sinAsistencia, camaraCaida);
    }

    // ---- The room at one instant --------------------------------------------------------------------------------

    /** People in the room: the students plus the teacher, who also occupies the room. */
    static int personas(Plan plan, double minuto) {
        return estudiantes(plan, minuto) + (docente(plan, minuto) ? 1 : 0);
    }

    /** The students of the class: they arrive after the start and leave before the end. */
    static int estudiantes(Plan plan, double minuto) {
        int i = plan.sesionEn(minuto);
        if (!plan.enCurso(i)) {
            return 0;
        }
        SesionDeClase sesion = plan.sesiones().get(i);
        double llegada = Math.min(1, (minuto - sesion.desde() + 2) / 15.0);
        double salida = Math.min(1, (sesion.hasta() - minuto) / 12.0);
        return (int) Math.round(plan.asistentes().get(i) * Math.max(0, Math.min(llegada, salida)));
    }

    /** The teacher is in the room from the start of the class until a few minutes before its end. */
    static boolean docente(Plan plan, double minuto) {
        int i = plan.sesionEn(minuto);
        return plan.enCurso(i) && minuto < plan.sesiones().get(i).hasta() - 4;
    }

    /** The camera drops for the middle part of the class that has the incident. */
    static boolean camaraEnLinea(Plan plan, double minuto) {
        int i = plan.sesionEn(minuto);
        if (!plan.enCurso(i) || !plan.camaraCaida().get(i)) {
            return true;
        }
        SesionDeClase sesion = plan.sesiones().get(i);
        return minuto < sesion.desde() + 20 || minuto >= sesion.hasta() - 15;
    }

    private SalaVista vista(CampusSpace space, int dia, double minuto) {
        Sala sala = sala(space);
        Plan plan = plan(sala, dia);
        int i = plan.sesionEn(minuto);
        Uso uso = i < 0 ? Uso.LIBRE : plan.sinAsistencia().get(i) ? Uso.RESERVA_SIN_USO : Uso.CLASE;
        int personas = personas(plan, minuto);
        boolean camara = camaraEnLinea(plan, minuto);
        List<PersonaVista> vistas = ubicar(sala, plan, dia, i, personas, minuto);
        String libreHasta = uso == Uso.CLASE ? null : hora(plan.siguienteDesde(minuto));

        List<String> hallazgos = new ArrayList<>();
        if (uso == Uso.RESERVA_SIN_USO) {
            hallazgos.add("Programado para " + plan.sesiones().get(i).etiqueta() + " y nadie llegó: libre hasta "
                    + libreHasta);
        }
        if (personas > space.capacity()) {
            hallazgos.add("Sobrecupo: " + personas + " personas para " + space.capacity() + " puestos");
        }
        if (!camara) {
            hallazgos.add("Cámara sin señal: el gemelo mantiene la última ocupación con un margen que crece");
        }

        List<Bloque> horario = FRANJAS.stream().map(franja -> bloque(plan, franja)).toList();
        Ahora ahora = new Ahora(uso, i < 0 ? null : plan.sesiones().get(i).etiqueta(), personas,
                vistas.stream().anyMatch(PersonaVista::docente), camara, libreHasta, vistas, hallazgos);
        return new SalaVista(space.spaceId(), space.name(), space.type(), space.buildingName(), space.floor(),
                space.capacity(), sala.filas(), sala.columnas(), horario, ahora);
    }

    /** The slot as the timetable grid shows it: the class that occupies it, or free. */
    private static Bloque bloque(Plan plan, Franja franja) {
        for (int i = 0; i < plan.sesiones().size(); i++) {
            SesionDeClase sesion = plan.sesiones().get(i);
            if (sesion.desde() < franja.hasta() && franja.desde() < sesion.hasta()) {
                List<String> partes = new ArrayList<>();
                if (plan.camaraCaida().get(i) && !plan.sinAsistencia().get(i)) {
                    partes.add("cámara caída un rato");
                }
                if (sesion.asignado()) {
                    partes.add("salón asignado por la demo");
                }
                return new Bloque(plan.sinAsistencia().get(i) ? Uso.RESERVA_SIN_USO : Uso.CLASE, sesion.etiqueta(),
                        partes.isEmpty() ? null : String.join(", ", partes));
            }
        }
        return new Bloque(Uso.LIBRE, null, null);
    }

    /**
     * Where the people are, on the floor as the camera projects them: the teacher in front of the board, students at
     * their seats (extra people stand at the back), with a small sway between instants.
     */
    private static List<PersonaVista> ubicar(Sala sala, Plan plan, int dia, int sesion, int personas, double minuto) {
        List<PersonaVista> vistas = new ArrayList<>();
        if (personas == 0) {
            return vistas;
        }
        Random lugar = rng(sala.space().spaceId(), dia, sesion);
        Random vaiven = rng(sala.space().spaceId(), (long) minuto);
        boolean hayDocente = docente(plan, minuto);
        int asientos = sala.filas() * sala.columnas();
        List<Integer> orden = new ArrayList<>();
        for (int i = 0; i < asientos; i++) {
            orden.add(i);
        }
        Collections.shuffle(orden, lugar);
        for (int i = 0; i < personas; i++) {
            double x;
            double y;
            boolean docente = hayDocente && i == 0;
            int alumno = hayDocente ? i - 1 : i;
            if (docente) {
                x = 0.5 + 0.15 * Math.sin(minuto / 3.0);
                y = 0.14;
            } else if (alumno < asientos) {
                int asiento = orden.get(alumno);
                x = (asiento % sala.columnas() + 0.5) / sala.columnas();
                y = 0.3 + 0.65 * (asiento / sala.columnas() + 0.5) / sala.filas();
            } else {
                x = 0.08 + 0.84 * lugar.nextDouble();
                y = 0.93 + 0.04 * lugar.nextDouble();
            }
            x = Math.min(0.98, Math.max(0.02, x + vaiven.nextGaussian() * 0.006));
            y = Math.min(0.98, Math.max(0.02, y + vaiven.nextGaussian() * 0.006));
            vistas.add(new PersonaVista(Math.round(x * 1000) / 1000.0, Math.round(y * 1000) / 1000.0, docente));
        }
        return vistas;
    }
}
