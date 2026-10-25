package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoomConditionPolicyTest {
    private final RoomConditionPolicy policy = new RoomConditionPolicy();
    private static final RoomEquipment OFF = RoomEquipment.off(30);
    private static final RoomEquipment LIGHTS = new RoomEquipment(true, 400, 0, 30);
    private static final RoomEquipment COMPUTERS = new RoomEquipment(false, 20, 12, 30);
    private static final RoomEquipment BOTH = new RoomEquipment(true, 400, 30, 30);

    @Test
    @DisplayName("Personas dentro: ocupado o sobrecupo, sin importar el equipo")
    void occupied() {
        assertEquals(RoomCondition.OCUPADO, policy.classify(false, 12, 30, BOTH));
        assertEquals(RoomCondition.SOBRECUPO, policy.classify(false, 31, 30, OFF));
    }

    @Test
    @DisplayName("Salón vacío: libre o desperdiciando energía según luces y equipos")
    void empty() {
        assertEquals(RoomCondition.LIBRE, policy.classify(false, 0, 30, OFF));
        assertEquals(RoomCondition.LUCES_SIN_OCUPACION, policy.classify(false, 0, 30, LIGHTS));
        assertEquals(RoomCondition.EQUIPOS_SIN_USO, policy.classify(false, 0, 30, COMPUTERS));
        assertEquals(RoomCondition.LUCES_Y_EQUIPOS_SIN_USO, policy.classify(false, 0, 30, BOTH));
        assertTrue(RoomCondition.LUCES_Y_EQUIPOS_SIN_USO.wastesEnergy());
    }

    @Test
    @DisplayName("Mantenimiento tiene prioridad")
    void maintenance() {
        assertEquals(RoomCondition.MANTENIMIENTO, policy.classify(true, 5, 30, BOTH));
    }

    @Test
    @DisplayName("No puede haber más equipos encendidos que equipos en el salón")
    void invalidEquipment() {
        assertThrows(DomainValidationException.class, () -> new RoomEquipment(false, 0, 31, 30));
    }
}
