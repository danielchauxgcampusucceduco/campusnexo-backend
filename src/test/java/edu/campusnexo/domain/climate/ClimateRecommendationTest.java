package edu.campusnexo.domain.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.occupancy.OccupancySource;
import edu.campusnexo.domain.shared.DomainValidationException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClimateRecommendationTest {
    private final HvacDecision hvac = new HvacDecision(HvacMode.CALEFACCION, 20.5, "frío");
    private final VentilationDecision ventilation = new VentilationDecision(VentilationMode.NINGUNA, "CO2 bien");
    private final LightingDecision lighting = new LightingDecision(30, false, "luz");

    private ClimateRecommendation.Builder complete() {
        return ClimateRecommendation.builder().spaceId("AULA-301").climateProfile(ClimateProfile.SEDE_ANDINA)
                .generatedAt(Instant.parse("2026-10-20T15:00:00Z")).hvac(hvac).ventilation(ventilation).lighting(lighting);
    }

    @Test
    @DisplayName("El Builder genera el identificador y aplica valores por defecto a las partes opcionales")
    void builderAppliesDefaults() {
        ClimateRecommendation recommendation = complete().build();
        assertNotNull(recommendation.recommendationId());
        assertEquals(OccupancySource.MANUAL, recommendation.occupancySource());
        assertNull(recommendation.inputs());
        assertNull(recommendation.sourceTelemetryAt());
    }

    @Test
    @DisplayName("El Builder rechaza recomendaciones incompletas")
    void builderRejectsMissingDecisions() {
        assertThrows(DomainValidationException.class, () -> complete().lighting(null).build());
        assertThrows(DomainValidationException.class, () -> complete().spaceId(" ").build());
    }

    @Test
    @DisplayName("Las explicaciones se derivan de las tres decisiones")
    void explanationsComeFromDecisions() {
        assertEquals(List.of("frío", "CO2 bien", "luz"), complete().build().explanations());
    }

    @Test
    @DisplayName("El setpoint conserva los decimales que usó la política")
    void setpointKeepsDecimals() {
        assertEquals(20.5, complete().build().hvac().setpointC());
    }
}
