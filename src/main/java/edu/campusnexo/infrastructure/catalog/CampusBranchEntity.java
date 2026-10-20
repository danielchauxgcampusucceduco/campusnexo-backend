package edu.campusnexo.infrastructure.catalog;

import edu.campusnexo.domain.model.ClimateProfile;
import jakarta.persistence.*;

@Entity
@Table(name = "campus_branches")
class CampusBranchEntity {
    @Id @Column(name = "branch_id", length = 32) private String branchId;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 80) private String city;
    @Enumerated(EnumType.STRING) @Column(name = "climate_profile", nullable = false, length = 24)
    private ClimateProfile climateProfile;
    @Column(name = "avg_outside_temp_c", nullable = false) private double averageOutsideTempC;
    protected CampusBranchEntity() {}
    String getBranchId() { return branchId; }
    String getName() { return name; }
    String getCity() { return city; }
    ClimateProfile getClimateProfile() { return climateProfile; }
    double getAverageOutsideTempC() { return averageOutsideTempC; }
}
