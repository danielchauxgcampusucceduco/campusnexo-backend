package edu.campusnexo.support;

import edu.campusnexo.domain.activo.EmparejamientoCpu;
import edu.campusnexo.domain.activo.PropuestaDePuestos;
import edu.campusnexo.domain.port.RegistroDePuestosRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRegistroDePuestosRepository implements RegistroDePuestosRepository {
    private final Map<String, PropuestaDePuestos> propuestas = new ConcurrentHashMap<>();
    private final Map<String, EmparejamientoCpu> emparejamientos = new ConcurrentHashMap<>();
    private final Map<String, String> agentes = new ConcurrentHashMap<>();

    @Override
    public void savePropuesta(PropuestaDePuestos propuesta) {
        propuestas.put(propuesta.spaceId(), propuesta);
    }

    @Override
    public Optional<PropuestaDePuestos> findPropuesta(String spaceId) {
        return Optional.ofNullable(propuestas.get(spaceId));
    }

    @Override
    public void deletePropuesta(String spaceId) {
        propuestas.remove(spaceId);
        emparejamientos.remove(spaceId);
    }

    @Override
    public void saveEmparejamiento(EmparejamientoCpu emparejamiento) {
        emparejamientos.put(emparejamiento.spaceId(), emparejamiento);
    }

    @Override
    public Optional<EmparejamientoCpu> findEmparejamiento(String spaceId) {
        return Optional.ofNullable(emparejamientos.get(spaceId));
    }

    @Override
    public void saveAgente(String cpu, String spaceId, Instant at) {
        agentes.put(cpu, spaceId);
    }

    @Override
    public List<String> findAgentes(String spaceId) {
        return agentes.entrySet().stream().filter(e -> e.getValue().equals(spaceId)).map(Map.Entry::getKey).sorted()
                .toList();
    }
}
