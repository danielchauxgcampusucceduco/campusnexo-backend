package edu.campusnexo.infrastructure.adapter.out.persistence.activo;

import edu.campusnexo.domain.activo.Activo;
import edu.campusnexo.domain.activo.Cpu;
import edu.campusnexo.domain.activo.TipoActivo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One row per asset with the seat it stands at; the workstation is not stored, it is the assets that share
 * space and seat number.
 */
@Entity
@Table(name = "activos")
class ActivoEntity {
    @Id
    @Column(name = "codigo", nullable = false, length = 40)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 8)
    private TipoActivo tipo;

    @Column(name = "space_id", nullable = false, length = 32)
    private String spaceId;

    @Column(name = "numero_puesto", nullable = false)
    private int numeroPuesto;

    @Column(name = "potencia_w", nullable = false)
    private double potenciaW;

    @Column(name = "fuera_de_servicio", nullable = false)
    private boolean fueraDeServicio;

    @Column(name = "ultimo_latido")
    private Instant ultimoLatido;

    protected ActivoEntity() {
    }

    ActivoEntity(String spaceId, int numeroPuesto, Activo activo) {
        codigo = activo.codigo();
        tipo = activo.tipo();
        this.spaceId = spaceId;
        this.numeroPuesto = numeroPuesto;
        potenciaW = activo.potenciaNominalW();
        fueraDeServicio = activo.fueraDeServicio();
        ultimoLatido = activo instanceof Cpu cpu ? cpu.ultimoLatido() : null;
    }

    String spaceId() {
        return spaceId;
    }

    int numeroPuesto() {
        return numeroPuesto;
    }

    Activo toDomain() {
        return tipo == TipoActivo.CPU ? new Cpu(codigo, potenciaW, fueraDeServicio, ultimoLatido)
                : Activo.registrar(tipo, codigo, potenciaW, fueraDeServicio);
    }
}
