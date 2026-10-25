package edu.campusnexo.application.persona;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.RegistrarPersonaCommand;
import edu.campusnexo.domain.persona.Docente;
import edu.campusnexo.domain.persona.TipoPersona;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.support.InMemoryPersonaRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersonaServiceTest {
    private final PersonaService service = new PersonaService(new InMemoryPersonaRepository());

    @Test
    @DisplayName("Registra docentes y estudiantes, los lista por tipo y actualiza por código")
    void registersListsAndUpdates() {
        service.registrar(new RegistrarPersonaCommand(TipoPersona.DOCENTE, "D-100", "Marta Ruiz", "Software"));
        service.registrar(new RegistrarPersonaCommand(TipoPersona.ESTUDIANTE, "E-200", "Juan Paz", "Sistemas"));
        service.registrar(new RegistrarPersonaCommand(TipoPersona.DOCENTE, "D-100", "Marta Ruiz Díaz", "Software"));

        assertEquals(2, service.listar(null).size());
        assertEquals(List.of("Marta Ruiz Díaz"), service.listar(TipoPersona.DOCENTE).stream().map(p -> p.nombre()).toList());
        assertInstanceOf(Docente.class, service.obtener("D-100"));
    }

    @Test
    @DisplayName("Eliminar o consultar a quien no existe responde no encontrado; el tipo es obligatorio")
    void missingPeopleAndKind() {
        assertThrows(ResourceNotFoundException.class, () -> service.obtener("X-1"));
        assertThrows(ResourceNotFoundException.class, () -> service.eliminar("X-1"));
        assertThrows(DomainValidationException.class,
                () -> service.registrar(new RegistrarPersonaCommand(null, "D-1", "Ana", "Software")));
    }
}
