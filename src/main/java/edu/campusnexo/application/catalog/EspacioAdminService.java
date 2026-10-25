package edu.campusnexo.application.catalog;

import edu.campusnexo.application.exception.ConflictException;
import edu.campusnexo.application.exception.SpaceNotFoundException;
import edu.campusnexo.application.port.in.EspacioAdminUseCase;
import edu.campusnexo.domain.activo.PuestoDeTrabajo;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.SeatLayout;
import edu.campusnexo.domain.port.ActivoRepository;
import edu.campusnexo.domain.port.SpaceCatalogRepository;
import edu.campusnexo.domain.shared.DomainValidationException;

/**
 * Applies the measurements to the catalog. A new seat grid may not leave a registered workstation outside it:
 * its code is a camera region of the old grid, so the administration removes or moves it first.
 */
public class EspacioAdminService implements EspacioAdminUseCase {
    private final SpaceCatalogRepository catalog;
    private final ActivoRepository activos;

    public EspacioAdminService(SpaceCatalogRepository catalog, ActivoRepository activos) {
        this.catalog = catalog;
        this.activos = activos;
    }

    @Override
    public CampusSpace ajustar(String spaceId, Medidas medidas) {
        CampusSpace actual = catalog.findById(spaceId).orElseThrow(() -> new SpaceNotFoundException(spaceId));
        if ((medidas.seatRows() == null) != (medidas.seatColumns() == null)) {
            throw new DomainValidationException("seatRows y seatColumns van juntos: ambos o ninguno");
        }
        SeatLayout layout = medidas.seatRows() == null ? null : new SeatLayout(medidas.seatRows(), medidas.seatColumns());
        CampusSpace ajustado = actual.conMedidas(medidas.capacity(), medidas.areaSqm(), layout, medidas.computers(),
                medidas.hvacInstalled());
        int seats = layout == null ? 0 : layout.seats();
        for (PuestoDeTrabajo puesto : activos.findPuestos(spaceId)) {
            if (puesto.numero() > seats) {
                throw new ConflictException("puestos_fuera_de_rejilla", "El puesto " + puesto.codigo()
                        + " quedaría fuera de la nueva rejilla de " + spaceId + "; retíralo antes de ajustar.");
            }
        }
        catalog.save(ajustado);
        return ajustado;
    }
}
