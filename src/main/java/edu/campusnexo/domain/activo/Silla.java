package edu.campusnexo.domain.activo;

public final class Silla extends Activo {
    public Silla(String codigo, boolean fueraDeServicio) {
        super(codigo, fueraDeServicio);
    }

    @Override
    public TipoActivo tipo() {
        return TipoActivo.SILLA;
    }
}
