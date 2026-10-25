package edu.campusnexo.application.caching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.port.SpaceCatalogRepository;
import edu.campusnexo.support.InMemorySpaceRepositoryPort;
import edu.campusnexo.support.MutableClock;
import edu.campusnexo.support.InMemorySpaceCatalogRepository;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CachingProxiesTest {
    private final MutableClock clock = new MutableClock(TestData.NOW);

    @Test
    @DisplayName("El proxy del catálogo lee PostgreSQL una vez por tiempo de vida, no en cada frame")
    void catalogIsReadOncePerTimeToLive() {
        AtomicInteger loads = new AtomicInteger();
        SpaceCatalogRepository subject = new SpaceCatalogRepository() {
            @Override
            public List<CampusSpace> findAll() {
                loads.incrementAndGet();
                return List.of(TestData.andeanClassroom(), TestData.lab());
            }

            @Override
            public Optional<CampusSpace> findById(String spaceId) {
                throw new AssertionError("el proxy no debe consultar espacio por espacio");
            }

            @Override
            public void save(CampusSpace space) {
                throw new AssertionError("esta prueba no escribe");
            }
        };
        CachingSpaceCatalogRepository catalog = new CachingSpaceCatalogRepository(subject, clock, Duration.ofMinutes(5));

        for (int i = 0; i < 100; i++) {
            assertEquals("Aula 301", catalog.findById("AULA-301").orElseThrow().name());
        }
        assertTrue(catalog.findById("NO-EXISTE").isEmpty());
        assertEquals(2, catalog.findAll().size());
        assertEquals(1, loads.get());

        clock.advance(Duration.ofMinutes(5));
        catalog.findById("AULA-301");
        assertEquals(2, loads.get(), "pasado el tiempo de vida se recarga");

        catalog.invalidate();
        catalog.findAll();
        assertEquals(3, loads.get());
    }

    @Test
    @DisplayName("Ajustar un salón escribe en el sujeto y descarta la copia en memoria")
    void savingGoesToTheSubjectAndDropsTheCopy() {
        InMemorySpaceCatalogRepository subject = new InMemorySpaceCatalogRepository(TestData.andeanClassroom());
        CachingSpaceCatalogRepository catalog = new CachingSpaceCatalogRepository(subject, clock, Duration.ofMinutes(5));
        CampusSpace room = catalog.findById("AULA-301").orElseThrow();

        catalog.save(room.conMedidas(40, 60, room.seatLayout(), room.computers(), room.hvacInstalled()));

        assertEquals(40, subject.findById("AULA-301").orElseThrow().capacity());
        assertEquals(40, catalog.findById("AULA-301").orElseThrow().capacity(), "sin esperar el tiempo de vida");
    }

    @Test
    @DisplayName("El proxy de regiones de pupitre las lee todas en una consulta")
    void seatRegionsAreReadInOneQuery() {
        InMemorySpaceRepositoryPort subject = new InMemorySpaceRepositoryPort()
                .withRois("BIB-PISO-2", InMemorySpaceRepositoryPort.libraryDesks());
        CachingSeatRegionRepository regions = new CachingSeatRegionRepository(subject, clock, Duration.ofMinutes(5));

        for (int i = 0; i < 50; i++) {
            assertEquals(8, regions.findRois("BIB-PISO-2").size());
            assertTrue(regions.findRois("AULA-301").isEmpty());
        }
        assertEquals(1, subject.roiQueries);
    }
}
