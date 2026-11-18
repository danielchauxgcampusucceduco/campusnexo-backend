package edu.campusnexo.infrastructure.adapter.out.horario;

import edu.campusnexo.domain.horario.SesionDeClase;
import edu.campusnexo.domain.port.HorarioAcademicoRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * The 2620 timetable of Software Engineering shipped with the backend ({@code tools/horario} regenerates it from the
 * faculty's spreadsheet). Read once: it only changes with a new release.
 */
@Repository
public class CsvHorarioAcademicoAdapter implements HorarioAcademicoRepository {
    static final String RECURSO = "/horario/horario-isw-2620.csv";
    private static final Map<String, DayOfWeek> DIAS = Map.of("LUNES", DayOfWeek.MONDAY, "MARTES", DayOfWeek.TUESDAY,
            "MIERCOLES", DayOfWeek.WEDNESDAY, "JUEVES", DayOfWeek.THURSDAY, "VIERNES", DayOfWeek.FRIDAY,
            "SABADO", DayOfWeek.SATURDAY);

    private final List<SesionDeClase> sesiones = cargar();

    @Override
    public List<SesionDeClase> sesiones() {
        return sesiones;
    }

    /** The timetable shipped with the backend, read from the classpath. */
    public static List<SesionDeClase> cargar() {
        try (InputStream in = CsvHorarioAcademicoAdapter.class.getResourceAsStream(RECURSO)) {
            if (in == null) {
                throw new IllegalStateException("Falta el horario académico " + RECURSO);
            }
            return leer(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Lines {@code grupo;estudiantes;curso;dia;inicio;fin;salon;asignado}, after the comments and the header. */
    public static List<SesionDeClase> leer(List<String> lineas) {
        return lineas.stream().filter(l -> !l.isBlank() && !l.startsWith("#") && !l.startsWith("grupo;"))
                .map(l -> l.split(";", -1))
                .map(c -> new SesionDeClase(c[0], Integer.parseInt(c[1]), c[2], dia(c[3]), Integer.parseInt(c[4]),
                        Integer.parseInt(c[5]), c[6], c[7].equals("si")))
                .toList();
    }

    private static DayOfWeek dia(String nombre) {
        DayOfWeek dia = DIAS.get(nombre);
        if (dia == null) {
            throw new IllegalStateException("Día desconocido en el horario: " + nombre);
        }
        return dia;
    }
}
