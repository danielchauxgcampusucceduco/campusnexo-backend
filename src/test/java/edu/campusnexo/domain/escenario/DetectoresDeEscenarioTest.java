package edu.campusnexo.domain.escenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.twin.Environment;
import edu.campusnexo.domain.twin.HvacControlState;
import edu.campusnexo.domain.twin.HvacSetting;
import edu.campusnexo.domain.twin.ManualControls;
import edu.campusnexo.domain.twin.Presence;
import edu.campusnexo.domain.twin.RoomCondition;
import edu.campusnexo.domain.twin.Sensing;
import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.vision.DeskObservation;
import edu.campusnexo.domain.vision.ZoneDeskState;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DetectoresDeEscenarioTest {
    private static final Instant NOW = TestData.NOW;
    private final ReconocedorDeEscenarios reconocedor = ReconocedorDeEscenarios.estandar();

    private static SpaceTwinState room(UnaryOperator<SpaceTwinState.Builder> change) {
        SpaceTwinState empty = SpaceTwinState.initial(TestData.room("303-A", SpaceType.AULA, 36, 6, 6, 1), NOW);
        return change.apply(empty.toBuilder().sensing(new Sensing(true, NOW, NOW, false))).build();
    }

    private List<EscenarioOperativo> recognize(UnaryOperator<SpaceTwinState.Builder> change) {
        return reconocedor.reconocer(room(change), NOW);
    }

    @Test
    @DisplayName("Un salón vacío y en orden no está en ningún escenario")
    void quietRoom() {
        assertTrue(recognize(b -> b).isEmpty());
    }

    @Test
    @DisplayName("Clase en curso: docente frente al tablero y al menos un estudiante")
    void classInProgress() {
        assertEquals(List.of(EscenarioOperativo.CLASE_EN_CURSO), recognize(b -> b.presence(new Presence(1, 24))));
        assertTrue(recognize(b -> b.presence(new Presence(0, 24))).isEmpty());
        assertTrue(recognize(b -> b.presence(new Presence(1, 0))).isEmpty());
    }

    @Test
    @DisplayName("Ocupación: sobrecupo, no-show, subutilizado y sala temporal se leen del estado del salón")
    void occupancyScenarios() {
        assertEquals(List.of(EscenarioOperativo.SOBRECUPO), recognize(b -> b.status(SpaceStatus.ALERTA_SOBRECUPO)));
        assertEquals(List.of(EscenarioOperativo.SOBRECUPO), recognize(b -> b.condition(RoomCondition.SOBRECUPO)));
        assertEquals(List.of(EscenarioOperativo.NO_SHOW), recognize(b -> b.status(SpaceStatus.LIBERADO_POR_NO_SHOW)));
        assertEquals(List.of(EscenarioOperativo.SUBUTILIZADO), recognize(b -> b.status(SpaceStatus.ALERTA_SUBUTILIZADO)));
        assertEquals(List.of(EscenarioOperativo.POPUP_PARCIALES),
                recognize(b -> b.controls(ManualControls.none().withPopUpUntil(NOW.plus(Duration.ofHours(2))))));
    }

    @Test
    @DisplayName("Ambiente: CO₂ alto por la lectura o por la ventilación del clima; cámara caída o callada")
    void environmentScenarios() {
        assertEquals(List.of(EscenarioOperativo.CO2_ALTO), recognize(b -> b.environment(new Environment(20, 14, 1_250, 50))));
        assertEquals(List.of(EscenarioOperativo.CO2_ALTO), recognize(b -> b.hvac(new HvacSetting(
                HvacControlState.VENTILACION_CO2_ACTIVA, HvacMode.APAGADO, 21, NOW, "CO₂"))));
        assertEquals(List.of(EscenarioOperativo.FALLO_CAMARA), recognize(b -> b.sensing(new Sensing(false, NOW, NOW, false))));
        assertEquals(List.of(EscenarioOperativo.FALLO_CAMARA),
                recognize(b -> b.sensing(new Sensing(true, NOW.minus(Duration.ofMinutes(5)), NOW, false))));
        assertFalse(recognize(b -> b.sensing(Sensing.none())).contains(EscenarioOperativo.FALLO_CAMARA),
                "un salón que nunca tuvo cámara no está fallando");
    }

    @Test
    @DisplayName("Energía y operación: luces o equipos sin uso, control docente, mantenimiento y acaparamiento")
    void energyAndOperationScenarios() {
        assertEquals(List.of(EscenarioOperativo.LUCES_ENCENDIDAS, EscenarioOperativo.EQUIPOS_ENCENDIDOS),
                recognize(b -> b.condition(RoomCondition.LUCES_Y_EQUIPOS_SIN_USO)));
        assertEquals(List.of(EscenarioOperativo.OVERRIDE_DOCENTE),
                recognize(b -> b.controls(ManualControls.none().withOverrideUntil(NOW.plus(Duration.ofMinutes(45))))));
        assertEquals(List.of(EscenarioOperativo.MANTENIMIENTO),
                recognize(b -> b.controls(ManualControls.none().withMaintenance(true))));
        assertEquals(List.of(EscenarioOperativo.ACAPARAMIENTO), recognize(b -> b.desks(List.of(new DeskObservation(
                "303-A-P01", ZoneDeskState.ALERTA_ACAPARAMIENTO, 0, 2, NOW, NOW)))));
    }
}
