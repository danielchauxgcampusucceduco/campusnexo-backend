package edu.campusnexo.infrastructure.adapter.out.persistence.activo;

import edu.campusnexo.domain.activo.Activo;
import edu.campusnexo.domain.activo.PuestoDeTrabajo;
import edu.campusnexo.domain.activo.TipoActivo;
import edu.campusnexo.domain.port.ActivoRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaActivoAdapter implements ActivoRepository {
    private final SpringDataActivoRepository data;

    JpaActivoAdapter(SpringDataActivoRepository data) {
        this.data = data;
    }

    @Override
    @Transactional
    public void save(String spaceId, int numeroPuesto, Activo activo) {
        data.save(new ActivoEntity(spaceId, numeroPuesto, activo));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PuestoDeTrabajo> findPuestos(String spaceId) {
        Map<Integer, List<Activo>> bySeat = data.findBySpaceIdOrderByNumeroPuestoAscCodigoAsc(spaceId).stream()
                .collect(Collectors.groupingBy(ActivoEntity::numeroPuesto, TreeMap::new,
                        Collectors.mapping(ActivoEntity::toDomain, Collectors.toList())));
        return bySeat.entrySet().stream()
                .map(seat -> new PuestoDeTrabajo(spaceId, seat.getKey(), seat.getValue()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PuestoDeTrabajo> findPuestoByCpu(String codigoCpu) {
        return data.findById(codigoCpu)
                .filter(entity -> entity.toDomain().tipo() == TipoActivo.CPU)
                .map(cpu -> new PuestoDeTrabajo(cpu.spaceId(), cpu.numeroPuesto(),
                        data.findBySpaceIdAndNumeroPuestoOrderByCodigoAsc(cpu.spaceId(), cpu.numeroPuesto()).stream()
                                .map(ActivoEntity::toDomain).toList()));
    }

    @Override
    @Transactional
    public void saveLatido(String codigoCpu, Instant at) {
        data.saveLatido(codigoCpu, at);
    }

    @Override
    @Transactional
    public boolean delete(String codigo) {
        if (!data.existsById(codigo)) {
            return false;
        }
        data.deleteById(codigo);
        return true;
    }
}
