package edu.campusnexo.infrastructure.adapter.out.persistence.persona;

import edu.campusnexo.domain.persona.Persona;
import edu.campusnexo.domain.persona.TipoPersona;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One table for every kind of person; {@code tipo} picks the class when reading it back. */
@Entity
@Table(name = "personas")
class PersonaEntity {
    @Id
    @Column(name = "codigo", nullable = false, length = 32)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 16)
    private TipoPersona tipo;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "adscripcion", nullable = false, length = 120)
    private String adscripcion;

    protected PersonaEntity() {
    }

    PersonaEntity(Persona persona) {
        codigo = persona.codigo();
        tipo = persona.tipo();
        nombre = persona.nombre();
        adscripcion = persona.adscripcion();
    }

    Persona toDomain() {
        return Persona.registrar(tipo, codigo, nombre, adscripcion);
    }
}
