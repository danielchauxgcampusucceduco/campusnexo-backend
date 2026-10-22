package edu.campusnexo.application.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.application.port.in.RecommendClimateCommand;
import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.climate.ClimateRecommendation;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.climate.policy.AndeanClimatePolicyFactory;
import edu.campusnexo.domain.climate.policy.MildClimatePolicyFactory;
import edu.campusnexo.domain.climate.policy.TropicalClimatePolicyFactory;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.twin.HvacControlState;
import edu.campusnexo.domain.twin.HvacSetting;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.support.InMemoryClimateRecommendationRepository;
import edu.campusnexo.support.InMemorySpaceCatalogRepository;
import edu.campusnexo.support.InMemorySpaceRepositoryPort;
import edu.campusnexo.support.MutableClock;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClimateRecommendationServiceTest {
    private final MutableClock clock = new MutableClock(TestData.NOW);
    private final InMemoryClimateRecommendationRepository repository = new InMemoryClimateRecommendationRepository();
    private final InMemorySpaceRepositoryPort twins = new InMemorySpaceRepositoryPort();
    private final ClimateRecommendationService service = new ClimateRecommendationService(clock,
            new SpaceCatalogService(new InMemorySpaceCatalogRepository(TestData.andeanClassroom(),
                    TestData.tropicalClassroom(), TestData.mildClassroom())),
            repository, new HvacStateResolver(twins, clock),
            List.of(new AndeanClimatePolicyFactory(), new TropicalClimatePolicyFactory(), new MildClimatePolicyFactory()));

    /** The twin says the room's unit has been heating since {@code since}. */
    private void unitHeatingSince(Instant since) {
        SpaceTwinState heating = SpaceTwinState.initial(TestData.andeanClassroom(), since).toBuilder()
                .hvac(new HvacSetting(HvacControlState.CONFORT_ACTIVO, HvacMode.CALEFACCION, 20, since, "Frío"))
                .build();
        twins.overwrite(heating);
    }

    private static RecommendClimateCommand command(String spaceId, double indoorC, double humidity) {
        return new RecommendClimateCommand(spaceId, 20, 700, indoorC, null, humidity, 300, null, null, null, null);
    }

    @Test
    @DisplayName("El mismo ambiente produce decisiones distintas según la familia climática de la sede")
    void selectsFamilyByBranchProfile() {
        ClimateRecommendation andean = service.recommend(command("AULA-301", 17, 60));
        ClimateRecommendation tropical = service.recommend(command("AULA-MAGNA-204", 17, 60));
        assertEquals(ClimateProfile.SEDE_ANDINA, andean.climateProfile());
        assertEquals(HvacMode.CALEFACCION, andean.hvac().mode());
        assertEquals(HvacMode.APAGADO, tropical.hvac().mode());
    }

    @Test
    @DisplayName("Usa la temperatura exterior promedio de la sede y guarda las entradas para auditoría")
    void auditsInputs() {
        ClimateRecommendation recommendation = service.recommend(command("AULA-301", 22, 60));
        assertNotNull(recommendation.inputs());
        assertEquals(14.5, recommendation.inputs().outsideTemperatureC());
        assertEquals(1, repository.findRecent(null, edu.campusnexo.domain.shared.ResultLimit.of(10)).size());
    }

    @Test
    @DisplayName("Sin estado HVAC enviado, toma el que el gemelo comandó al equipo y respeta su ciclo mínimo")
    void takesTheRealHvacStateFromTheTwin() {
        unitHeatingSince(clock.instant().minus(Duration.ofMinutes(3)));
        ClimateRecommendation young = service.recommend(command("AULA-301", 22, 60));
        assertEquals(HvacMode.CALEFACCION, young.hvac().mode(), "el compresor lleva 3 min: debe seguir encendido");
        assertEquals(3, young.inputs().currentHvac().minutesInMode());

        clock.advance(Duration.ofMinutes(10));
        assertEquals(HvacMode.APAGADO, service.recommend(command("AULA-301", 22, 60)).hvac().mode());
    }

    @Test
    @DisplayName("Sin gemelo todavía, asume el equipo apagado desde hace mucho")
    void withoutTwinTheUnitIsAssumedOffForLong() {
        ClimateRecommendation recommendation = service.recommend(command("AULA-301", 17, 60));
        assertEquals(HvacMode.CALEFACCION, recommendation.hvac().mode());
        assertEquals(HvacMode.APAGADO, recommendation.inputs().currentHvac().mode());
    }

    @Test
    @DisplayName("El estado HVAC se envía completo o no se envía")
    void hvacStateMustBeComplete() {
        RecommendClimateCommand partial = new RecommendClimateCommand("AULA-301", 20, 700, 22, null, 60, 300,
                HvacMode.APAGADO, null, null, null);
        assertThrows(DomainValidationException.class, () -> service.recommend(partial));
    }

    @Test
    @DisplayName("Falla al iniciar si falta la fábrica de algún perfil")
    void requiresAFactoryPerProfile() {
        assertThrows(IllegalStateException.class, () -> new ClimateRecommendationService(clock,
                new SpaceCatalogService(new InMemorySpaceCatalogRepository()), repository,
                new HvacStateResolver(twins, clock), List.of(new AndeanClimatePolicyFactory())));
    }
}
