package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OccupancyBeliefTest {

    @Test
    void roundsTheMeanAndComputesStandardDeviation() {
        OccupancyBelief belief = new OccupancyBelief(12.6, 9, Instant.parse("2026-10-21T15:00:00Z"));

        assertEquals(13, belief.people());
        assertEquals(3, belief.standardDeviation());
    }

    @Test
    void createsAnUninformativeEmptyPriorForTheRoomCapacity() {
        OccupancyBelief prior = OccupancyBelief.uninformed(Instant.EPOCH, 40);

        assertEquals(0, prior.estimate());
        assertEquals(400, prior.variance());
    }

    @Test
    void rejectsNonFiniteBeliefs() {
        assertThrows(DomainValidationException.class,
                () -> new OccupancyBelief(Double.NaN, 1, Instant.now()));
        assertThrows(DomainValidationException.class,
                () -> new OccupancyBelief(1, Double.POSITIVE_INFINITY, Instant.now()));
    }
}
