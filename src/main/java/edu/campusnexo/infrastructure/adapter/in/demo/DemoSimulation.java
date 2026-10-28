package edu.campusnexo.infrastructure.adapter.in.demo;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SeatLayout;
import edu.campusnexo.domain.catalog.SpaceType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.regex.Pattern;

/**
 * Compressed academic day (07:00 to 22:00) for the system demo, as a pure function of the wall clock. Each demo room
 * gets a seeded schedule per simulated day, and every instant says what the room holds (people, lights, devices) and
 * what a normal camera and an infrared camera see of it. The normal camera loses people in the dark and cannot tell
 * whether a device without a screen is on; the infrared camera sees body heat and hot equipment, even a device that
 * was turned off a few minutes ago. Situations are exaggerated on purpose (forgotten lights and projectors, labs left
 * with equipment on, classes in the dark, overcrowding, a normal camera down, reservations nobody used, maintenance)
 * so one run of the loop shows every case.
 */
public final class DemoSimulation {
    static final int DAY_START = 7 * 60;
    static final int DAY_END = 22 * 60;
    public static final List<Franja> FRANJAS = List.of(
            new Franja("07:00–09:00", 420, 540), new Franja("09:00–11:00", 540, 660),
            new Franja("11:00–13:00", 660, 780), new Franja("13:00–14:00 almuerzo", 780, 840),
            new Franja("14:00–16:00", 840, 960), new Franja("16:00–18:00", 960, 1080),
            new Franja("18:00–20:00", 1080, 1200), new Franja("20:00–22:00", 1200, 1320));
    private static final int LUNCH = 3;
    private static final Pattern MEDICINE = Pattern.compile(
            "cl[ií]nic|cuidado|morfolog|microbiolog|preclin|historias|anatom", Pattern.CASE_INSENSITIVE);

    public enum Uso { CLASE, PRACTICA, RESERVA_SIN_USO, MANTENIMIENTO, LIBRE }

    /** What the last group left on in a free slot. */
    enum Olvido { LUCES, PANTALLA, EQUIPOS }

    enum Incidente { A_OSCURAS, SOBRECUPO, CAMARA_NORMAL_CAIDA }

    public record Franja(String etiqueta, int desde, int hasta) {
        boolean contiene(double minuto) {
            return minuto >= desde && minuto < hasta;
        }
    }

    /** One device, or a group of identical ones (the PCs of a room) with {@code cantidad > 1}. */
    record Equipo(String nombre, String categoria, int potenciaW, boolean pantalla, boolean siempreEncendido,
                  int cantidad, Double x, Double y) {
        Equipo at(int cantidad, Double x, Double y) {
            return new Equipo(nombre, categoria, potenciaW, pantalla, siempreEncendido, cantidad, x, y);
        }

        double calorC() {
            return Math.min(45, 6 + potenciaW / 35.0);
        }
    }

    private static Equipo medico(String nombre, int w, boolean pantalla, boolean siempre) {
        return new Equipo(nombre, "MEDICINA", w, pantalla, siempre, 1, null, null);
    }

    private static Equipo ingenieria(String nombre, int w, boolean pantalla, boolean siempre) {
        return new Equipo(nombre, "INGENIERIA", w, pantalla, siempre, 1, null, null);
    }

    private static final List<Equipo> MEDICINA = List.of(
            medico("Monitor de signos vitales", 60, true, false), medico("Simulador de paciente", 250, true, false),
            medico("Desfibrilador de entrenamiento", 40, true, false), medico("Ventilador mecánico", 150, true, false),
            medico("Microscopio", 30, false, false), medico("Centrífuga", 400, false, false),
            medico("Autoclave", 1500, false, false), medico("Nevera de reactivos", 200, false, true),
            medico("Incubadora", 300, false, true), medico("Cabina de flujo laminar", 350, false, false));
    private static final List<Equipo> INGENIERIA = List.of(
            ingenieria("Osciloscopio", 50, true, false), ingenieria("Fuente de poder", 120, false, false),
            ingenieria("Generador de señales", 40, true, false), ingenieria("Impresora 3D", 250, false, false),
            ingenieria("Controlador PLC", 60, true, false), ingenieria("Espectrofotómetro", 90, true, false),
            ingenieria("Plancha de calentamiento", 800, false, false), ingenieria("Biorreactor", 500, false, true),
            ingenieria("Campana de extracción", 600, false, false),
            ingenieria("Gafas de realidad mixta", 20, true, false));
    private static final List<String> CURSOS_COMPUTO = List.of("Programación I", "Bases de datos", "Redes",
            "Estructuras de datos", "Ingeniería de software", "Sistemas operativos");
    private static final List<String> CURSOS_AULA = List.of("Cálculo diferencial", "Física I", "Ética", "Inglés III",
            "Estadística", "Derecho constitucional", "Contabilidad");
    private static final List<String> CURSOS_MEDICINA = List.of("Anatomía", "Semiología", "Microbiología",
            "Fisiología", "Urgencias simuladas");
    private static final List<String> CURSOS_INGENIERIA = List.of("Química general", "Electrónica", "Física II",
            "Investigación de operaciones", "Bioprocesos");

    private final List<CampusSpace> salones;
    private final Duration dayLength;

    public DemoSimulation(List<CampusSpace> salones, Duration dayLength) {
        this.salones = List.copyOf(salones);
        this.dayLength = dayLength;
    }

    /** The demo set: computer rooms and labs (not the hardware pilots 301-A and 303-A) plus some classrooms. */
    public static List<CampusSpace> demoRooms(List<CampusSpace> catalog, int classrooms) {
        List<CampusSpace> rooms = new ArrayList<>();
        catalog.stream().filter(s -> !Set.of("301-A", "303-A").contains(s.spaceId()))
                .filter(s -> s.type() == SpaceType.SALA_COMPUTO || s.type() == SpaceType.LABORATORIO)
                .sorted((a, b) -> a.spaceId().compareTo(b.spaceId())).forEach(rooms::add);
        catalog.stream().filter(s -> s.type() == SpaceType.AULA && !s.spaceId().equals("303-A"))
                .sorted((a, b) -> a.spaceId().compareTo(b.spaceId())).limit(classrooms).forEach(rooms::add);
        return rooms;
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

    // ---- Output -------------------------------------------------------------------------------------------------

    public record Snapshot(Instant generadoEn, int dia, String hora, int minutoDelDia, int franjaActual,
                           double minutosSimuladosPorMinuto, List<Franja> franjas, List<SalaVista> salones) {
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
        int franja = franjaDe(minuto);
        List<SalaVista> vistas = salones.stream().map(s -> vista(s, dia, minuto)).toList();
        int entero = (int) minuto;
        return new Snapshot(now, dia, String.format(Locale.ROOT, "%02d:%02d", entero / 60, entero % 60), entero,
                franja, (DAY_END - DAY_START) / (dayMs / 60_000.0), FRANJAS, vistas);
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
        boolean ocupada(Uso uso) {
            return uso == Uso.CLASE || uso == Uso.PRACTICA;
        }
    }

    record Plan(List<Uso> usos, List<String> cursos, List<Integer> asistentes, List<Incidente> incidentes,
                List<Set<Olvido>> olvidos, List<Set<Integer>> enPractica, List<Set<Integer>> olvidados) {
    }

    static Sala sala(CampusSpace space) {
        SeatLayout layout = space.seatLayout();
        int columnas = layout != null ? layout.columns() : (int) Math.ceil(Math.sqrt(Math.max(1, space.capacity())));
        int filas = layout != null ? layout.rows() : (int) Math.ceil(Math.max(1, space.capacity()) / (double) columnas);
        List<Equipo> equipos = new ArrayList<>();
        int hash = Math.abs(space.spaceId().hashCode());
        boolean computo = space.type() == SpaceType.SALA_COMPUTO;
        boolean laboratorio = space.type() == SpaceType.LABORATORIO;
        if (computo) {
            // Computer rooms have one or the other, depending on the room.
            equipos.add(new Equipo(hash % 2 == 0 ? "Video beam" : "Televisor", "PROYECCION",
                    hash % 2 == 0 ? 280 : 150, true, false, 1, 0.5, 0.03));
        } else if (laboratorio) {
            if (space.capacity() >= 15) {
                equipos.add(new Equipo("Televisor", "PROYECCION", 150, true, false, 1, 0.5, 0.03));
            }
        } else {
            equipos.add(new Equipo("Video beam", "PROYECCION", 280, true, false, 1, 0.5, 0.03));
        }
        if (space.computers() > 0) {
            String nombre = computo ? "Computadores" : space.computers() == 1 ? "PC del docente" : "Computadores";
            equipos.add(new Equipo(nombre, "COMPUTO", 120, true, false, space.computers(), null, null));
        }
        if (laboratorio) {
            List<Equipo> kit = MEDICINE.matcher(space.name()).find() ? MEDICINA : INGENIERIA;
            Random rng = rng(space.spaceId());
            List<Equipo> elegidos = new ArrayList<>(kit);
            java.util.Collections.shuffle(elegidos, rng);
            int n = Math.min(kit.size(), 4 + (int) Math.round(Math.min(4, space.capacity() / 8.0)));
            List<Equipo> propios = new ArrayList<>(elegidos.subList(0, n));
            if (space.name().toLowerCase(Locale.ROOT).contains("realidad")) {
                propios.removeIf(e -> e.nombre().startsWith("Gafas"));
                propios.add(0, ingenieria("Gafas de realidad mixta", 20, true, false));
            }
            for (int i = 0; i < propios.size(); i++) {
                double[] p = perimetro((i + 0.5) / propios.size());
                equipos.add(propios.get(i).at(1, p[0], p[1]));
            }
        }
        return new Sala(space, List.copyOf(equipos), filas, columnas);
    }

    /** A point along the left wall, the back wall and the right wall, for {@code t} from 0 to 1. */
    private static double[] perimetro(double t) {
        double lado = 0.75;
        double total = lado * 2 + 0.92;
        double d = t * total;
        if (d < lado) {
            return new double[] {0.04, 0.2 + d};
        }
        if (d < lado + 0.92) {
            return new double[] {0.04 + (d - lado), 0.96};
        }
        return new double[] {0.96, 0.96 - (d - lado - 0.92)};
    }

    static Plan plan(Sala sala, int dia) {
        CampusSpace space = sala.space();
        Random rng = rng(space.spaceId(), dia);
        boolean laboratorio = space.type() == SpaceType.LABORATORIO;
        List<String> cursos = cursosDe(space);
        int n = FRANJAS.size();
        List<Uso> usos = new ArrayList<>();
        List<String> nombres = new ArrayList<>();
        List<Integer> asistentes = new ArrayList<>();
        List<Incidente> incidentes = new ArrayList<>();
        List<Set<Olvido>> olvidos = new ArrayList<>();
        List<Set<Integer>> enPractica = new ArrayList<>();
        List<Set<Integer>> olvidados = new ArrayList<>();
        boolean mantenimiento = false;
        for (int f = 0; f < n; f++) {
            Uso uso = Uso.LIBRE;
            if (f != LUNCH) {
                double r = rng.nextDouble();
                double ocupacion = f == n - 1 ? 0.35 : 0.55;
                if (r < ocupacion) {
                    uso = laboratorio ? Uso.PRACTICA : Uso.CLASE;
                } else if (r < ocupacion + 0.12) {
                    uso = Uso.RESERVA_SIN_USO;
                } else if (r < ocupacion + 0.19 && !mantenimiento) {
                    uso = Uso.MANTENIMIENTO;
                    mantenimiento = true;
                }
            }
            usos.add(uso);
            nombres.add(uso == Uso.LIBRE || uso == Uso.MANTENIMIENTO ? null : cursos.get(rng.nextInt(cursos.size())));
            Incidente incidente = null;
            int capacidad = Math.max(1, space.capacity());
            int gente = 0;
            if (sala.ocupada(uso)) {
                if (rng.nextDouble() < 0.45) {
                    Incidente[] posibles = laboratorio
                            ? new Incidente[] {Incidente.SOBRECUPO, Incidente.CAMARA_NORMAL_CAIDA, Incidente.A_OSCURAS}
                            : new Incidente[] {Incidente.A_OSCURAS, Incidente.A_OSCURAS, Incidente.SOBRECUPO,
                                    Incidente.CAMARA_NORMAL_CAIDA};
                    incidente = posibles[rng.nextInt(posibles.length)];
                }
                double fraccion = incidente == Incidente.SOBRECUPO ? 1.15 + rng.nextDouble() * 0.25
                        : 0.45 + rng.nextDouble() * 0.5;
                gente = Math.max(3, (int) Math.round(capacidad * fraccion));
            }
            incidentes.add(incidente);
            asistentes.add(gente);
            Set<Integer> practica = new LinkedHashSet<>();
            Set<Integer> dejados = new LinkedHashSet<>();
            for (int i = 0; i < sala.equipos().size(); i++) {
                Equipo equipo = sala.equipos().get(i);
                if (!equipo.categoria().equals("PROYECCION") && !equipo.categoria().equals("COMPUTO")
                        && sala.ocupada(uso) && rng.nextDouble() < 0.6) {
                    practica.add(i);
                }
            }
            EnumSet<Olvido> dejado = EnumSet.noneOf(Olvido.class);
            if (uso == Uso.LIBRE && f > 0 && sala.ocupada(usos.get(f - 1)) && rng.nextDouble() < 0.7) {
                if (rng.nextDouble() < 0.6) {
                    dejado.add(Olvido.LUCES);
                }
                if (rng.nextDouble() < 0.55 && sala.equipos().stream().anyMatch(e -> e.categoria().equals("PROYECCION"))) {
                    dejado.add(Olvido.PANTALLA);
                }
                if (rng.nextDouble() < 0.5 || dejado.isEmpty()) {
                    dejado.add(Olvido.EQUIPOS);
                    for (int i : enPractica.get(f - 1)) {
                        if (rng.nextDouble() < 0.4) {
                            dejados.add(i);
                        }
                    }
                }
            }
            olvidos.add(dejado);
            enPractica.add(practica);
            olvidados.add(dejados);
        }
        return new Plan(usos, nombres, asistentes, incidentes, olvidos, enPractica, olvidados);
    }

    private static List<String> cursosDe(CampusSpace space) {
        return switch (space.type()) {
            case SALA_COMPUTO -> CURSOS_COMPUTO;
            case LABORATORIO -> MEDICINE.matcher(space.name()).find() ? CURSOS_MEDICINA : CURSOS_INGENIERIA;
            default -> CURSOS_AULA;
        };
    }

    // ---- The room at one instant --------------------------------------------------------------------------------

    /** People in the room: the class ramps up after the start and empties before the end. */
    static int personas(Sala sala, Plan plan, double minuto) {
        int f = franjaDe(minuto);
        Uso uso = plan.usos().get(f);
        if (uso == Uso.MANTENIMIENTO) {
            return 1;
        }
        if (!sala.ocupada(uso)) {
            return 0;
        }
        Franja franja = FRANJAS.get(f);
        double llegada = Math.min(1, (minuto - franja.desde() + 2) / 15.0);
        double salida = Math.min(1, (franja.hasta() - minuto) / 12.0);
        return (int) Math.round(plan.asistentes().get(f) * Math.max(0, Math.min(llegada, salida)));
    }

    static boolean luces(Sala sala, Plan plan, double minuto) {
        int f = franjaDe(minuto);
        Uso uso = plan.usos().get(f);
        Franja franja = FRANJAS.get(f);
        if (uso == Uso.MANTENIMIENTO) {
            return true;
        }
        if (sala.ocupada(uso)) {
            boolean proyectando = plan.incidentes().get(f) == Incidente.A_OSCURAS
                    && minuto >= franja.desde() + 15 && minuto < franja.hasta() - 20;
            return !proyectando && personas(sala, plan, minuto) > 0;
        }
        return plan.olvidos().get(f).contains(Olvido.LUCES);
    }

    /** How many of the device's units are on. */
    static int encendidos(Sala sala, Plan plan, int indice, double minuto) {
        Equipo equipo = sala.equipos().get(indice);
        if (equipo.siempreEncendido()) {
            return equipo.cantidad();
        }
        int f = franjaDe(minuto);
        Uso uso = plan.usos().get(f);
        Franja franja = FRANJAS.get(f);
        Set<Olvido> olvidos = plan.olvidos().get(f);
        boolean enClase = sala.ocupada(uso) && minuto >= franja.desde() + 3 && minuto < franja.hasta() - 6;
        return switch (equipo.categoria()) {
            case "PROYECCION" -> enClase || olvidos.contains(Olvido.PANTALLA) ? 1 : 0;
            case "COMPUTO" -> {
                if (enClase) {
                    // One PC per student; the teacher is at the board.
                    yield sala.space().type() == SpaceType.SALA_COMPUTO
                            ? Math.min(equipo.cantidad(), Math.max(0, personas(sala, plan, minuto) - 1)) : equipo.cantidad();
                }
                yield olvidos.contains(Olvido.EQUIPOS) ? (int) Math.ceil(equipo.cantidad() * 0.4) : 0;
            }
            default -> (enClase && minuto >= franja.desde() + 10 && plan.enPractica().get(f).contains(indice))
                    || plan.olvidados().get(f).contains(indice) ? 1 : 0;
        };
    }

    static double ambiente(int personas, int capacidad, double minuto) {
        double hora = (minuto - DAY_START) / 60.0;
        return 15 + 4 * Math.sin(Math.PI * hora / 15) + 2.5 * personas / Math.max(1, capacidad);
    }

    private SalaVista vista(CampusSpace space, int dia, double minuto) {
        Sala sala = sala(space);
        Plan plan = plan(sala, dia);
        int f = franjaDe(minuto);
        Uso uso = plan.usos().get(f);
        Incidente incidente = plan.incidentes().get(f);
        int personas = personas(sala, plan, minuto);
        boolean luces = luces(sala, plan, minuto);
        boolean camaraNormal = incidente != Incidente.CAMARA_NORMAL_CAIDA || !sala.ocupada(uso);
        double ambiente = ambiente(personas, space.capacity(), minuto);

        List<DispositivoVista> dispositivos = new ArrayList<>();
        double potenciaW = luces ? 12 * Math.max(10, space.areaSqm()) : 0;
        int encendidosTotal = 0;
        for (int i = 0; i < sala.equipos().size(); i++) {
            Equipo equipo = sala.equipos().get(i);
            int on = encendidos(sala, plan, i, minuto);
            potenciaW += on * equipo.potenciaW();
            if (!equipo.siempreEncendido()) {
                encendidosTotal += on;
            }
            double temperatura = ambiente + (on > 0 ? equipo.calorC() : equipo.calorC() * enfriamiento(sala, plan, i, minuto));
            String termico = temperatura > ambiente + 4 ? "CALIENTE" : temperatura > ambiente + 1.5 ? "TIBIO" : "FRIO";
            Boolean vistoNormal = equipo.pantalla() && camaraNormal ? Boolean.valueOf(on > 0) : null;
            dispositivos.add(new DispositivoVista(equipo.nombre(), equipo.categoria(), equipo.cantidad(), on,
                    equipo.pantalla(), equipo.siempreEncendido(), vistoNormal, termico,
                    Math.round(temperatura * 10) / 10.0, equipo.x(), equipo.y()));
        }

        List<PersonaVista> vistas = ubicar(sala, plan, dia, f, personas, luces, camaraNormal, minuto);
        Integer vistasNormal = camaraNormal ? (int) vistas.stream().filter(PersonaVista::visibleCamaraNormal).count() : null;
        double co2 = 430 + 900.0 * personas / Math.max(1, space.capacity());

        List<String> hallazgos = new ArrayList<>();
        String pantalla = sala.equipos().stream().filter(e -> e.categoria().equals("PROYECCION")).map(Equipo::nombre)
                .findFirst().orElse("proyector").toLowerCase(Locale.ROOT);
        if (uso == Uso.RESERVA_SIN_USO) {
            hallazgos.add("Reservado para " + plan.cursos().get(f) + " y nadie llegó");
        }
        if (uso == Uso.MANTENIMIENTO) {
            hallazgos.add("En mantenimiento: un técnico trabajando");
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

        List<Bloque> horario = new ArrayList<>();
        for (int i = 0; i < FRANJAS.size(); i++) {
            horario.add(new Bloque(plan.usos().get(i), plan.cursos().get(i), detalle(plan, i)));
        }
        Ahora ahora = new Ahora(uso, plan.cursos().get(f), personas, vistasNormal, personas,
                vistas.stream().anyMatch(PersonaVista::docente), luces, camaraNormal,
                Math.round(ambiente * 10) / 10.0, Math.round(co2), Math.round(potenciaW / 10.0) / 100.0,
                encendidosTotal, dispositivos, vistas, hallazgos);
        return new SalaVista(space.spaceId(), space.name(), space.type(), space.buildingName(), space.floor(),
                space.capacity(), sala.filas(), sala.columnas(), horario, ahora);
    }

    private static String detalle(Plan plan, int f) {
        List<String> partes = new ArrayList<>();
        Incidente incidente = plan.incidentes().get(f);
        if (incidente == Incidente.A_OSCURAS) {
            partes.add("proyección a oscuras");
        } else if (incidente == Incidente.SOBRECUPO) {
            partes.add("sobrecupo");
        } else if (incidente == Incidente.CAMARA_NORMAL_CAIDA) {
            partes.add("cámara normal caída");
        }
        for (Olvido olvido : plan.olvidos().get(f)) {
            partes.add(switch (olvido) {
                case LUCES -> "luces olvidadas";
                case PANTALLA -> "pantalla olvidada";
                case EQUIPOS -> "equipos olvidados";
            });
        }
        return partes.isEmpty() ? null : String.join(", ", partes);
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
    private static List<PersonaVista> ubicar(Sala sala, Plan plan, int dia, int f, int personas, boolean luces,
                                             boolean camaraNormal, double minuto) {
        List<PersonaVista> vistas = new ArrayList<>();
        if (personas == 0) {
            return vistas;
        }
        Random lugar = rng(sala.space().spaceId(), dia, f);
        Random vaiven = rng(sala.space().spaceId(), (long) minuto);
        boolean hayDocente = sala.ocupada(plan.usos().get(f));
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
            if (docente) {
                x = 0.5 + 0.15 * Math.sin(minuto / 3.0);
                y = 0.14;
            } else if (!hayDocente) {
                x = 0.3 + 0.4 * lugar.nextDouble();
                y = 0.4 + 0.3 * lugar.nextDouble();
            } else if (i - 1 < asientos) {
                int asiento = orden.get(i - 1);
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
