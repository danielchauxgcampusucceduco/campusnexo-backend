package edu.campusnexo.infrastructure.adapter.out.persistence.activo;

import edu.campusnexo.domain.activo.EmparejamientoCpu;
import edu.campusnexo.domain.activo.PropuestaDePuestos;
import edu.campusnexo.domain.activo.PuestoPropuesto;
import edu.campusnexo.domain.port.RegistroDePuestosRepository;
import edu.campusnexo.infrastructure.adapter.out.persistence.activo.RegistroEntities.AgenteEntity;
import edu.campusnexo.infrastructure.adapter.out.persistence.activo.RegistroEntities.EmparejamientoEntity;
import edu.campusnexo.infrastructure.adapter.out.persistence.activo.RegistroEntities.PropuestaEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** The proposal's seats and the pairing's computers are small lists, stored as JSON next to their room. */
@Repository
class JpaRegistroDePuestosAdapter implements RegistroDePuestosRepository {
    private final SpringDataPropuestaRepository propuestas;
    private final SpringDataEmparejamientoRepository emparejamientos;
    private final SpringDataAgenteRepository agentes;
    private final JsonMapper json;

    JpaRegistroDePuestosAdapter(SpringDataPropuestaRepository propuestas,
                                SpringDataEmparejamientoRepository emparejamientos, SpringDataAgenteRepository agentes,
                                JsonMapper json) {
        this.propuestas = propuestas;
        this.emparejamientos = emparejamientos;
        this.agentes = agentes;
        this.json = json;
    }

    @Override
    @Transactional
    public void savePropuesta(PropuestaDePuestos propuesta) {
        PropuestaEntity entity = new PropuestaEntity();
        entity.spaceId = propuesta.spaceId();
        entity.recibidaAt = propuesta.recibidaAt();
        entity.emulada = propuesta.emulada();
        entity.puestos = json.writeValueAsString(propuesta.puestos());
        propuestas.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PropuestaDePuestos> findPropuesta(String spaceId) {
        return propuestas.findById(spaceId).map(entity -> new PropuestaDePuestos(entity.spaceId, entity.recibidaAt,
                entity.emulada, Arrays.asList(json.readValue(entity.puestos, PuestoPropuesto[].class))));
    }

    @Override
    @Transactional
    public void deletePropuesta(String spaceId) {
        propuestas.deleteById(spaceId);
        emparejamientos.deleteById(spaceId);
    }

    @Override
    @Transactional
    public void saveEmparejamiento(EmparejamientoCpu emparejamiento) {
        EmparejamientoEntity entity = new EmparejamientoEntity();
        entity.spaceId = emparejamiento.spaceId();
        entity.inicio = emparejamiento.inicio();
        entity.ranuraMs = emparejamiento.ranura().toMillis();
        entity.cpus = json.writeValueAsString(emparejamiento.cpus());
        emparejamientos.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmparejamientoCpu> findEmparejamiento(String spaceId) {
        return emparejamientos.findById(spaceId).map(entity -> new EmparejamientoCpu(entity.spaceId, entity.inicio,
                Duration.ofMillis(entity.ranuraMs), Arrays.asList(json.readValue(entity.cpus, String[].class))));
    }

    @Override
    @Transactional
    public void saveAgente(String cpu, String spaceId, Instant at) {
        AgenteEntity entity = new AgenteEntity();
        entity.cpu = cpu;
        entity.spaceId = spaceId;
        entity.ultimoLatido = at;
        agentes.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findAgentes(String spaceId) {
        return agentes.findBySpaceIdOrderByCpuAsc(spaceId).stream().map(entity -> entity.cpu).toList();
    }
}
