package edu.campusnexo.support;

import edu.campusnexo.domain.activo.Activo;
import edu.campusnexo.domain.activo.Cpu;
import edu.campusnexo.domain.activo.PuestoDeTrabajo;
import edu.campusnexo.domain.port.ActivoRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class InMemoryActivoRepository implements ActivoRepository {
    private record Row(String spaceId, int numero, Activo activo) {
    }

    private final List<Row> rows = new ArrayList<>();

    @Override
    public synchronized void save(String spaceId, int numeroPuesto, Activo activo) {
        rows.removeIf(row -> row.activo().codigo().equals(activo.codigo()));
        rows.add(new Row(spaceId, numeroPuesto, activo));
    }

    @Override
    public synchronized List<PuestoDeTrabajo> findPuestos(String spaceId) {
        Map<Integer, List<Activo>> bySeat = rows.stream().filter(row -> row.spaceId().equals(spaceId))
                .collect(Collectors.groupingBy(Row::numero, TreeMap::new,
                        Collectors.mapping(Row::activo, Collectors.toList())));
        return bySeat.entrySet().stream().map(seat -> new PuestoDeTrabajo(spaceId, seat.getKey(), seat.getValue()))
                .toList();
    }

    @Override
    public synchronized Optional<PuestoDeTrabajo> findPuestoByCpu(String codigoCpu) {
        return rows.stream().filter(row -> row.activo() instanceof Cpu && row.activo().codigo().equals(codigoCpu))
                .findFirst()
                .map(cpu -> new PuestoDeTrabajo(cpu.spaceId(), cpu.numero(), rows.stream()
                        .filter(row -> row.spaceId().equals(cpu.spaceId()) && row.numero() == cpu.numero())
                        .map(Row::activo).toList()));
    }

    @Override
    public synchronized void saveLatido(String codigoCpu, Instant at) {
        rows.replaceAll(row -> row.activo() instanceof Cpu cpu && cpu.codigo().equals(codigoCpu)
                ? new Row(row.spaceId(), row.numero(), cpu.conLatido(at)) : row);
    }

    @Override
    public synchronized boolean delete(String codigo) {
        return rows.removeIf(row -> row.activo().codigo().equals(codigo));
    }
}
