package edu.campusnexo.domain.escenario;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;

import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Runs every detector over a room and lists the scenarios it is in, in the order of {@link EscenarioOperativo}. */
public final class ReconocedorDeEscenarios {
    private final List<DetectorDeEscenario> detectores;

    public ReconocedorDeEscenarios(List<DetectorDeEscenario> detectores) {
        this.detectores = List.copyOf(requireNonNull(detectores, "detectores"));
    }

    /** One detector per scenario. */
    public static ReconocedorDeEscenarios estandar() {
        return new ReconocedorDeEscenarios(List.of(
                new ClaseEnCurso(), new Co2Alto(), new FalloCamara(), new Sobrecupo(), new NoShow(),
                new Subutilizado(), new PopupParciales(), new LucesEncendidas(), new EquiposEncendidos(),
                new OverrideDocente(), new Mantenimiento(), new Acaparamiento()));
    }

    public List<EscenarioOperativo> reconocer(SpaceTwinState estado, Instant ahora) {
        requireNonNull(estado, "estado");
        requireNonNull(ahora, "ahora");
        return detectores.stream()
                .filter(detector -> detector.reconoce(estado, ahora))
                .map(DetectorDeEscenario::escenario)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
