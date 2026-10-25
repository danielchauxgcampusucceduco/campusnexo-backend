package edu.campusnexo.domain.persona;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.shared.DomainValidationException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersonaTest {
    private final Persona docente = new Docente("D-100", "Marta Ruiz", "Ingeniería de Software");
    private final Persona estudiante = new Estudiante("E-200", "Juan Paz", "Ingeniería de Sistemas");

    @Test
    @DisplayName("Cada tipo de persona responde a su manera qué puede reservar y por cuánto tiempo")
    void bookingRulesArePolymorphic() {
        for (Persona persona : List.of(docente, estudiante)) {
            assertTrue(persona.puedeReservar(SpaceType.SALA_ESTUDIO), persona.toString());
            assertFalse(persona.puedeReservar(SpaceType.ADMINISTRATIVA), persona.toString());
        }
        assertTrue(docente.puedeReservar(SpaceType.SALA_COMPUTO));
        assertFalse(estudiante.puedeReservar(SpaceType.AULA));
        assertTrue(estudiante.puedeReservar(SpaceType.BIBLIOTECA));
        assertEquals(Duration.ofHours(4), docente.reservaMaxima());
        assertEquals(Duration.ofHours(2), estudiante.reservaMaxima());
    }

    @Test
    @DisplayName("Se registra por tipo y se identifica solo por su código institucional")
    void registeredByKindAndIdentifiedByCode() {
        Persona registered = Persona.registrar(TipoPersona.DOCENTE, " D-100 ", "Marta Ruiz", "Ingeniería de Software");
        assertInstanceOf(Docente.class, registered);
        assertEquals("D-100", registered.codigo());
        assertEquals(docente, registered);
        assertEquals("Ingeniería de Sistemas", estudiante.adscripcion());
        assertEquals(TipoPersona.ESTUDIANTE, estudiante.tipo());
    }

    @Test
    @DisplayName("Código, nombre y adscripción son obligatorios")
    void requiredFields() {
        assertThrows(DomainValidationException.class, () -> new Docente(" ", "Marta", "Software"));
        assertThrows(DomainValidationException.class, () -> new Estudiante("E-1", "", "Sistemas"));
        assertThrows(DomainValidationException.class, () -> new Estudiante("E-1", "Juan", null));
        assertThrows(DomainValidationException.class, () -> new Docente("D-" + "9".repeat(40), "Marta", "Software"));
    }
}
