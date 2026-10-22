package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.catalog.ClimateProfile;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Exactly one climate family per climate profile, checked when the application starts: a missing or
 * duplicated family is a configuration error, never a runtime surprise in one room.
 */
public final class ClimateFamilies {
    private final Map<ClimateProfile, ClimatePolicyFactory> byProfile;

    public ClimateFamilies(List<ClimatePolicyFactory> factories) {
        EnumMap<ClimateProfile, ClimatePolicyFactory> families = new EnumMap<>(ClimateProfile.class);
        for (ClimatePolicyFactory factory : factories) {
            if (families.put(factory.climateProfile(), factory) != null) {
                throw new IllegalStateException("Hay más de una fábrica para " + factory.climateProfile());
            }
        }
        for (ClimateProfile profile : ClimateProfile.values()) {
            if (!families.containsKey(profile)) {
                throw new IllegalStateException("Falta la fábrica de políticas para " + profile);
            }
        }
        this.byProfile = Map.copyOf(families);
    }

    /** The three families of the campus network: Andean, tropical and mild. */
    public static ClimateFamilies standard() {
        return new ClimateFamilies(List.of(new AndeanClimatePolicyFactory(), new TropicalClimatePolicyFactory(),
                new MildClimatePolicyFactory()));
    }

    public ClimatePolicyFactory of(ClimateProfile profile) {
        return byProfile.get(profile);
    }
}
