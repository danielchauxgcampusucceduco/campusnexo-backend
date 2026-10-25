package edu.campusnexo.infrastructure.adapter.out.persistence.catalog;

import edu.campusnexo.domain.catalog.ClimateProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "campus_branches")
class CampusBranchEntity {
    @Id
    @Column(name = "branch_id", length = 32)
    private String branchId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "climate_profile", nullable = false, length = 24)
    private ClimateProfile climateProfile;

    @Column(name = "avg_outside_temp_c", nullable = false)
    private double averageOutsideTempC;

    @Column(name = "kwh_tariff_cop", nullable = false, precision = 10, scale = 2)
    private BigDecimal kwhTariffCop;

    protected CampusBranchEntity() {
    }

    String getBranchId() {
        return branchId;
    }

    String getName() {
        return name;
    }

    String getCity() {
        return city;
    }

    ClimateProfile getClimateProfile() {
        return climateProfile;
    }

    double getAverageOutsideTempC() {
        return averageOutsideTempC;
    }

    BigDecimal getKwhTariffCop() {
        return kwhTariffCop;
    }
}
