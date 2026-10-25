package edu.campusnexo.application.occupancy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.campusnexo.domain.twin.KalmanParameters;
import edu.campusnexo.domain.twin.OccupancyKalmanDomainService.FusedOccupancy;
import edu.campusnexo.domain.twin.OccupancyKalmanDomainService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The reference cases of the occupancy fusion model. The AI service runs the very same file
 * ({@code tests/fusion_golden_cases.csv} there), so the twin, the local fallback and the service cannot drift
 * apart without one of the two test suites failing.
 */
class FusionModelTest {
    private final OccupancyKalmanDomainService fusion = new OccupancyKalmanDomainService(KalmanParameters.defaults());

    private static List<String[]> cases() throws IOException {
        try (InputStream in = FusionModelTest.class.getResourceAsStream("/fusion_golden_cases.csv")) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return text.lines().filter(line -> !line.isBlank() && !line.startsWith("#")).skip(1)
                    .map(line -> line.split(",")).toList();
        }
    }

    @Test
    @DisplayName("La fusión coincide con los casos de referencia compartidos con el servicio de IA")
    void matchesTheSharedReferenceCases() throws IOException {
        List<String[]> cases = cases();
        assertEquals(6, cases.size());
        for (String[] row : cases) {
            Integer camera = row[1].isBlank() ? null : Integer.valueOf(row[1]);
            FusedOccupancy fused = fusion.fuse(camera, Double.parseDouble(row[2]), Integer.parseInt(row[3]),
                    Double.parseDouble(row[4]), Double.parseDouble(row[5]));
            assertEquals(Double.parseDouble(row[6]), fused.estimate(), 0.01, row[0] + " estimate");
            assertEquals(Double.parseDouble(row[7]), fused.standardDeviation(), 0.01, row[0] + " sigma");
            assertEquals(Double.parseDouble(row[9]), fused.confidencePct(Integer.parseInt(row[8])), 0.05, row[0] + " confidence");
        }
    }
}
