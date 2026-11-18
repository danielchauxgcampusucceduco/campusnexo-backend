package edu.campusnexo.domain.port;

import edu.campusnexo.domain.horario.SesionDeClase;
import java.util.List;

/** The academic timetable of the campus: the classes of every group, week by week. */
public interface HorarioAcademicoRepository {
    List<SesionDeClase> sesiones();
}
