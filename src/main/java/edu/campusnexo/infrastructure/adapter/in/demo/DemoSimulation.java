package edu.campusnexo.infrastructure.adapter.in.demo;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SeatLayout;
import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.vision.BoundingBox;
import edu.campusnexo.domain.vision.DetectedObjectClass;
import edu.campusnexo.domain.vision.NormalizedPoint;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import edu.campusnexo.domain.vision.TrackedDetection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

/**
 * Compressed academic week for the system demo, as a pure function of the wall clock: each loop is one weekday of the
 * 2620 timetable of Software Engineering (Monday to Friday, 07:00 to 22:00, in one-hour slots), and only the rooms that
 * timetable uses take part. Every session happens in its room with its course and group: the enrolled students arrive
 * (most of them, as in a real class) and the teacher with them. What surrounds the timetable is simulated around it:
 * lights, projectors and computers left on after a class, a projection in the dark, a normal camera that drops, and now
 * and then a class nobody attends. Every instant says what each room holds and what a normal and an infrared camera
 * see of it: the normal camera loses people in the dark and cannot tell whether a device without a screen is on; the
 * infrared camera sees body heat and hot equipment, even a device turned off minutes ago.
 */
public final class DemoSimulation {
    static final int DAY_START = 7 * 60;
    static final int DAY_END = 22 * 60;
    public static final List<Franja> FRANJAS = IntStream.range(7, 22)
            .mapToObj(h -> new Franja(String.format(Locale.ROOT, "%02d:00–%02d:00", h, h + 1), h * 60, (h + 1) * 60))
            .toList();
    public static final List<String> DIAS = List.of("LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES");
    private static final List<String> NOMBRES_DIA = List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes");
    static final String HORARIO = "/demo/horario-isw-2620.csv";
    public static final String PROGRAMA = "Ingeniería de Software · horario 2620";

    /** RESERVA_SIN_USO: a class of the timetable that nobody attended. */
    public enum Uso { CLASE, RESERVA_SIN_USO, LIBRE }

    /** What the group left on after its class. */
    enum Olvido { LUCES, PANTALLA, EQUIPOS }

    enum Incidente { A_OSCURAS, CAMARA_NORMAL_CAIDA }

    public record Franja(String etiqueta, int desde, int hasta) {
        boolean contiene(double minuto) {
            return minuto >= desde && minuto < hasta;
        }
    }

    /**
     * One class of the timetable, from {@code inicio} to {@code fin} (whole hours). {@code asignado}: the timetable
     * gave it no room, or put it in a room another group was using, and the timetable conversion chose a free one.
     */
    public record Sesion(String grupo, int estudiantes, String curso, String dia, int inicio, int fin, String salon,
                         boolean asignado) {
        int desde() {
            return inicio * 60;
        }

        int hasta() {
            return fin * 60;
        }

        String etiqueta() {
            return curso + " · " + grupo;
        }
    }

    /** One device, or a group of identical ones (the PCs of a room) with {@code cantidad > 1}. */
    record Equipo(String nombre, String categoria, int potenciaW, boolean pantalla, boolean siempreEncendido,
                  int cantidad, Double x, Double y) {
        double calorC() {
            return Math.min(45, 6 + potenciaW / 35.0);
        }
    }

    private final List<CampusSpace> salones;
    private final List<Sesion> horario;
    private final Duration dayLength;

    public DemoSimulation(List<CampusSpace> salones, List<Sesion> horario, Duration dayLength) {
        this.salones = List.copyOf(salones);
        this.horario = List.copyOf(horario);
        this.dayLength = dayLength;
    }

    /** The sessions of the timetable shipped with the backend ({@code tools/horario} regenerates it). */
    public static List<Sesion> horario() {
        try (InputStream in = DemoSimulation.class.getResourceAsStream(HORARIO)) {
            if (in == null) {
                throw new IllegalStateException("Falta el horario de la demo " + HORARIO);
            }
            return leer(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Lines {@code grupo;estudiantes;curso;dia;inicio;fin;salon;asignado}, after the comments and the header. */
    static List<Sesion> leer(List<String> lineas) {
        return lineas.stream().filter(l -> !l.isBlank() && !l.startsWith("#") && !l.startsWith("grupo;"))
                .map(l -> l.split(";", -1))
                .map(c -> new Sesion(c[0], Integer.parseInt(c[1]), c[2], c[3], Integer.parseInt(c[4]),
                        Integer.parseInt(c[5]), c[6], c[7].equals("si")))
                .toList();
    }

    /** The demo set: the rooms of the timetable that the catalog has, computer rooms first. */
    public static List<CampusSpace> demoRooms(List<CampusSpace> catalog, List<Sesion> horario) {
        Set<String> usados = horario.stream().map(Sesion::salon).collect(java.util.stream.Collectors.toSet());
        return catalog.stream().filter(s -> usados.contains(s.spaceId()))
                .sorted(Comparator.comparing((CampusSpace s) -> s.type() == SpaceType.SALA_COMPUTO ? 0 : 1)
                        .thenComparing(CampusSpace::spaceId))
                .toList();
    }

    public List<CampusSpace> salones() {
        return salones;
    }

    /**
     * A generator seeded from the parts. {@code new Random(seed)} with close seeds gives correlated first draws
     * (every person hidden in the dark, the same plan on consecutive days), so the seed is mixed first.
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

    /** The plan of one slot; {@code detalle} names what was left on or what goes wrong in it. */
    public record Bloque(Uso uso, String curso, String detalle) {
    }

    public record Ahora(Uso uso, String curso, int personas, Integer personasCamaraNormal, int personasInfrarroja,
                        boolean docente, boolean luces, boolean camaraNormalEnLinea, double temperaturaC,
                        double co2Ppm, double potenciaKw, int dispositivosEncendidos,
                        List<DispositivoVista> dispositivos, List<PersonaVista> personasVistas,
                        List<String> hallazgos) {
    }

    /**
     * {@code camaraNormal}: whether the normal camera sees it on (a lit screen), {@code null} when it cannot tell (no
     * screen). {@code termico}: CALIENTE (on), TIBIO (turned off minutes ago) or FRIO, as the infrared camera sees it.
     */
    public record DispositivoVista(String nombre, String categoria, int cantidad, int encendidos, boolean pantalla,
                                   boolean siempreEncendido, Boolean camaraNormal, String termico, double temperaturaC,
                                   Double x, Double y) {
    }

    public record PersonaVista(double x, double y, boolean docente, boolean visibleCamaraNormal) {
    }

    public Snapshot snapshot(Instant now) {
        long dayMs = dayLength.toMillis();
        int dia = (int) Math.floorDiv(now.toEpochMilli(), dayMs);
        double minuto = DAY_START + (double) Math.floorMod(now.toEpochMilli(), dayMs) / dayMs * (DAY_END - DAY_START);
        List<SalaVista> vistas = salones.stream().map(s -> vista(s, dia, minuto)).toList();
        int entero = (int) minuto;
        return new Snapshot(now, dia, NOMBRES_DIA.get(Math.floorMod(dia, DIAS.size())), PROGRAMA,
                String.format(Locale.ROOT, "%02d:%02d", entero / 60, entero % 60), entero, franjaDe(minuto),
                (DAY_END - DAY_START) / (dayMs / 60_000.0), FRANJAS, vistas);
    }

    static int franjaDe(double minuto) {
        for (int i = 0; i < FRANJAS.size(); i++) {
            if (FRANJAS.get(i).contiene(minuto)) {
                return i;
            }
        }
        return FRANJAS.size() - 1;
    }

    // ---- The day plan of a room ---------------------------------------------------------------------------------

    record Sala(CampusSpace space, List<Equipo> equipos, int filas, int columnas) {
        boolean computo() {
            return space.type() == SpaceType.SALA_COMPUTO;
        }
    }

    /**
     * The sessions of the room on that weekday and, per session, who attends, what goes wrong and what the group leaves
     * on afterwards (until {@code olvidoHasta}, in minutes of the day).
     */
    record Plan(List<Sesion> sesiones, List<Integer> asistentes, List<Boolean> sinAsistencia,
                List<Incidente> incidentes, List<Set<Olvido>> olvidos, List<Integer> olvidoHasta) {
        int sesionEn(double minuto) {
            for (int i = 0; i < sesiones.size(); i++) {
                if (minuto >= sesiones.get(i).desde() && minuto < sesiones.get(i).hasta()) {
                    return i;
                }
            }
            return -1;
        }

        /** The session whose leftovers are still on at that minute, when no class is running, or -1. */
        int olvidoEn(double minuto) {
            if (sesionEn(minuto) >= 0) {
                return -1;
            }
            for (int i = 0; i < sesiones.size(); i++) {
                if (minuto >= sesiones.get(i).hasta() && minuto < olvidoHasta.get(i)) {
                    return i;
                }
            }
            return -1;
        }

        boolean enCurso(int i) {
            return i >= 0 && !sinAsistencia.get(i);
        }
    }

    static Sala sala(CampusSpace space) {
        SeatLayout layout = space.seatLayout();
        int columnas = layout != null ? layout.columns() : (int) Math.ceil(Math.sqrt(Math.max(1, space.capacity())));
        int filas = layout != null ? layout.rows() : (int) Math.ceil(Math.max(1, space.capacity()) / (double) columnas);
        List<Equipo> equipos = new ArrayList<>();
        int hash = Math.abs(space.spaceId().hashCode());
        boolean computo = space.type() == SpaceType.SALA_COMPUTO;
        // Computer rooms have a video beam or a television, depending on the room; classrooms a video beam.
        boolean videoBeam = !computo || hash % 2 == 0;
        equipos.add(new Equipo(videoBeam ? "Video beam" : "Televisor", "PROYECCION", videoBeam ? 280 : 150, true, false,
                1, 0.5, 0.03));
        if (space.computers() > 0) {
            equipos.add(new Equipo(computo ? "Computadores" : "PC del docente", "COMPUTO", 120, true, false,
                    space.computers(), null, null));
        }
        return new Sala(space, List.copyOf(equipos), filas, columnas);
    }

    Plan plan(Sala sala, int dia) {
        String weekday = DIAS.get(Math.floorMod(dia, DIAS.size()));
        List<Sesion> sesiones = horario.stream()
                .filter(s -> s.salon().equals(sala.space().spaceId()) && s.dia().equals(weekday))
                .sorted(Comparator.comparingInt(Sesion::inicio)).toList();
        List<Integer> asistentes = new ArrayList<>();
        List<Boolean> sinAsistencia = new ArrayList<>();
        List<Incidente> incidentes = new ArrayList<>();
        List<Set<Olvido>> olvidos = new ArrayList<>();
        List<Integer> olvidoHasta = new ArrayList<>();
        for (int i = 0; i < sesiones.size(); i++) {
            Sesion sesion = sesiones.get(i);
            Random rng = rng(sala.space().spaceId(), dia, sesion.inicio());
            // Most of the group comes; once in a while a class of the timetable has nobody.
            asistentes.add(Math.max(3, (int) Math.round(sesion.estudiantes() * (0.72 + rng.nextDouble() * 0.23))));
            sinAsistencia.add(rng.nextDouble() < 0.05);
            Incidente incidente = null;
            if (rng.nextDouble() < 0.35) {
                incidente = rng.nextDouble() < (sala.computo() ? 0.5 : 0.6) ? Incidente.A_OSCURAS
                        : Incidente.CAMARA_NORMAL_CAIDA;
            }
            incidentes.add(incidente);
            int siguiente = i + 1 < sesiones.size() ? sesiones.get(i + 1).desde() : DAY_END;
            EnumSet<Olvido> dejado = EnumSet.noneOf(Olvido.class);
            if (siguiente > sesion.hasta() && rng.nextDouble() < 0.6) {
                if (rng.nextDouble() < 0.6) {
                    dejado.add(Olvido.LUCES);
                }
                if (rng.nextDouble() < 0.55) {
                    dejado.add(Olvido.PANTALLA);
                }
                if (rng.nextDouble() < 0.5 || dejado.isEmpty()) {
                    dejado.add(Olvido.EQUIPOS);
                }
            }
            olvidos.add(dejado);
            olvidoHasta.add(dejado.isEmpty() ? sesion.hasta() : Math.min(siguiente, sesion.hasta() + 60));
        }
        return new Plan(sesiones, asistentes, sinAsistencia, incidentes, olvidos, olvidoHasta);
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
        Sesion sesion = plan.sesiones().get(i);
        double llegada = Math.min(1, (minuto - sesion.desde() + 2) / 15.0);
        double salida = Math.min(1, (sesion.hasta() - minuto) / 12.0);
        return (int) Math.round(plan.asistentes().get(i) * Math.max(0, Math.min(llegada, salida)));
    }

    /** The teacher is in the room from the start of the class until a few minutes before its end. */
    static boolean docente(Plan plan, double minuto) {
        int i = plan.sesionEn(minuto);
        return plan.enCurso(i) && minuto < plan.sesiones().get(i).hasta() - 4;
    }

    static boolean luces(Plan plan, double minuto) {
        int i = plan.sesionEn(minuto);
        if (plan.enCurso(i)) {
            Sesion sesion = plan.sesiones().get(i);
            boolean proyectando = plan.incidentes().get(i) == Incidente.A_OSCURAS
                    && minuto >= sesion.desde() + 15 && minuto < sesion.hasta() - 20;
            return !proyectando && personas(plan, minuto) > 0;
        }
        int olvido = plan.olvidoEn(minuto);
        return olvido >= 0 && plan.olvidos().get(olvido).contains(Olvido.LUCES);
    }

    /** How many of the device's units are on. */
    static int encendidos(Sala sala, Plan plan, int indice, double minuto) {
        Equipo equipo = sala.equipos().get(indice);
        if (equipo.siempreEncendido()) {
            return equipo.cantidad();
        }
        int i = plan.sesionEn(minuto);
        boolean enClase = plan.enCurso(i) && minuto >= plan.sesiones().get(i).desde() + 3
                && minuto < plan.sesiones().get(i).hasta() - 6;
        int olvido = plan.olvidoEn(minuto);
        Set<Olvido> dejado = olvido >= 0 ? plan.olvidos().get(olvido) : Set.of();
        return switch (equipo.categoria()) {
            case "PROYECCION" -> enClase || dejado.contains(Olvido.PANTALLA) ? 1 : 0;
            case "COMPUTO" -> {
                if (enClase) {
                    // One PC per student in a computer room; the teacher works at the board.
                    yield sala.computo() ? Math.min(equipo.cantidad(), estudiantes(plan, minuto)) : equipo.cantidad();
                }
                yield dejado.contains(Olvido.EQUIPOS) ? (int) Math.ceil(equipo.cantidad() * 0.4) : 0;
            }
            default -> 0;
        };
    }

    static double ambiente(int personas, int capacidad, double minuto) {
        double hora = (minuto - DAY_START) / 60.0;
        return 15 + 4 * Math.sin(Math.PI * hora / 15) + 2.5 * personas / Math.max(1, capacidad);
    }

    private SalaVista vista(CampusSpace space, int dia, double minuto) {
        Sala sala = sala(space);
        Plan plan = plan(sala, dia);
        int i = plan.sesionEn(minuto);
        Uso uso = i < 0 ? Uso.LIBRE : plan.sinAsistencia().get(i) ? Uso.RESERVA_SIN_USO : Uso.CLASE;
        Incidente incidente = plan.enCurso(i) ? plan.incidentes().get(i) : null;
        int personas = personas(plan, minuto);
        boolean luces = luces(plan, minuto);
        boolean camaraNormal = incidente != Incidente.CAMARA_NORMAL_CAIDA;
        double ambiente = ambiente(personas, space.capacity(), minuto);

        List<DispositivoVista> dispositivos = new ArrayList<>();
        double potenciaW = luces ? 12 * Math.max(10, space.areaSqm()) : 0;
        int encendidosTotal = 0;
        for (int e = 0; e < sala.equipos().size(); e++) {
            Equipo equipo = sala.equipos().get(e);
            int on = encendidos(sala, plan, e, minuto);
            potenciaW += on * equipo.potenciaW();
            if (!equipo.siempreEncendido()) {
                encendidosTotal += on;
            }
            double temperatura = ambiente + (on > 0 ? equipo.calorC() : equipo.calorC() * enfriamiento(sala, plan, e, minuto));
            String termico = temperatura > ambiente + 4 ? "CALIENTE" : temperatura > ambiente + 1.5 ? "TIBIO" : "FRIO";
            Boolean vistoNormal = equipo.pantalla() && camaraNormal ? Boolean.valueOf(on > 0) : null;
            dispositivos.add(new DispositivoVista(equipo.nombre(), equipo.categoria(), equipo.cantidad(), on,
                    equipo.pantalla(), equipo.siempreEncendido(), vistoNormal, termico,
                    Math.round(temperatura * 10) / 10.0, equipo.x(), equipo.y()));
        }

        List<PersonaVista> vistas = ubicar(sala, plan, dia, i, personas, luces, camaraNormal, minuto);
        Integer vistasNormal = camaraNormal ? (int) vistas.stream().filter(PersonaVista::visibleCamaraNormal).count() : null;
        double co2 = 430 + 900.0 * personas / Math.max(1, space.capacity());

        List<String> hallazgos = new ArrayList<>();
        String pantalla = sala.equipos().getFirst().nombre().toLowerCase(Locale.ROOT);
        if (uso == Uso.RESERVA_SIN_USO) {
            hallazgos.add("Programado para " + plan.sesiones().get(i).etiqueta() + " y nadie llegó");
        }
        if (personas == 0 && luces) {
            hallazgos.add("Vacío con las luces encendidas");
        }
        if (personas == 0 && dispositivos.stream().anyMatch(d -> d.categoria().equals("PROYECCION") && d.encendidos() > 0)) {
            hallazgos.add("Vacío con el " + pantalla + " encendido");
        }
        List<String> olvidados = dispositivos.stream()
                .filter(d -> personas == 0 && d.encendidos() > 0 && !d.siempreEncendido()
                        && !d.categoria().equals("PROYECCION"))
                .map(d -> d.cantidad() > 1 ? d.encendidos() + " " + d.nombre().toLowerCase(Locale.ROOT) : d.nombre())
                .toList();
        if (!olvidados.isEmpty()) {
            hallazgos.add("Vacío con equipos encendidos: " + String.join(", ", olvidados));
        }
        if (personas > 0 && !luces && camaraNormal) {
            hallazgos.add("A oscuras: la cámara normal ve " + vistasNormal + " de " + personas
                    + "; la infrarroja las ve a todas");
        }
        if (personas > space.capacity()) {
            hallazgos.add("Sobrecupo: " + personas + " personas para " + space.capacity() + " puestos");
        }
        if (!camaraNormal) {
            hallazgos.add("Cámara normal sin señal; la infrarroja sostiene el conteo");
        }

        List<Bloque> horario = FRANJAS.stream().map(franja -> bloque(plan, franja)).toList();
        Ahora ahora = new Ahora(uso, i < 0 ? null : plan.sesiones().get(i).etiqueta(), personas, vistasNormal, personas,
                vistas.stream().anyMatch(PersonaVista::docente), luces, camaraNormal,
                Math.round(ambiente * 10) / 10.0, Math.round(co2), Math.round(potenciaW / 10.0) / 100.0,
                encendidosTotal, dispositivos, vistas, hallazgos);
        return new SalaVista(space.spaceId(), space.name(), space.type(), space.buildingName(), space.floor(),
                space.capacity(), sala.filas(), sala.columnas(), horario, ahora);
    }

    /** The slot as the timetable grid shows it: the class that occupies it, or what was left on in it. */
    private static Bloque bloque(Plan plan, Franja franja) {
        for (int i = 0; i < plan.sesiones().size(); i++) {
            Sesion sesion = plan.sesiones().get(i);
            if (sesion.desde() < franja.hasta() && franja.desde() < sesion.hasta()) {
                List<String> partes = new ArrayList<>();
                if (plan.incidentes().get(i) == Incidente.A_OSCURAS) {
                    partes.add("proyección a oscuras");
                } else if (plan.incidentes().get(i) == Incidente.CAMARA_NORMAL_CAIDA) {
                    partes.add("cámara normal caída");
                }
                if (sesion.asignado()) {
                    partes.add("salón asignado por la demo");
                }
                return new Bloque(plan.sinAsistencia().get(i) ? Uso.RESERVA_SIN_USO : Uso.CLASE, sesion.etiqueta(),
                        partes.isEmpty() ? null : String.join(", ", partes));
            }
        }
        for (int i = 0; i < plan.sesiones().size(); i++) {
            Sesion sesion = plan.sesiones().get(i);
            if (sesion.hasta() < franja.hasta() && franja.desde() < plan.olvidoHasta().get(i)) {
                List<String> partes = plan.olvidos().get(i).stream().map(o -> switch (o) {
                    case LUCES -> "luces olvidadas";
                    case PANTALLA -> "pantalla olvidada";
                    case EQUIPOS -> "equipos olvidados";
                }).toList();
                return new Bloque(Uso.LIBRE, null, String.join(", ", partes));
            }
        }
        return new Bloque(Uso.LIBRE, null, null);
    }

    /** Residual heat of a device that is off: 1 just after turning off, fading over about 10 simulated minutes. */
    private static double enfriamiento(Sala sala, Plan plan, int indice, double minuto) {
        for (int atras = 1; atras <= 40; atras++) {
            if (minuto - atras < DAY_START) {
                return 0;
            }
            if (encendidos(sala, plan, indice, minuto - atras) > 0) {
                return Math.exp(-atras / 8.0);
            }
        }
        return 0;
    }

    /**
     * Where the people are: the teacher in front of the board, students at their seats (extra people stand at the
     * back), with a small sway between instants. In the dark the normal camera sees about a third of them.
     */
    private static List<PersonaVista> ubicar(Sala sala, Plan plan, int dia, int sesion, int personas, boolean luces,
                                             boolean camaraNormal, double minuto) {
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
        java.util.Collections.shuffle(orden, lugar);
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
            boolean visible = camaraNormal && (luces || rng(sala.space().spaceId(), i, (long) (minuto / 4)).nextDouble() < 0.3);
            vistas.add(new PersonaVista(Math.round(x * 1000) / 1000.0, Math.round(y * 1000) / 1000.0, docente, visible));
        }
        return vistas;
    }
}
