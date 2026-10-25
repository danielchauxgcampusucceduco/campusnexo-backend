package edu.campusnexo.domain.activo;

public final class Mesa extends Activo {
    public Mesa(String codigo, boolean fueraDeServicio) {
        super(codigo, fueraDeServicio);
    }

    @Override
    public TipoActivo tipo() {
        return TipoActivo.MESA;
    }
}
