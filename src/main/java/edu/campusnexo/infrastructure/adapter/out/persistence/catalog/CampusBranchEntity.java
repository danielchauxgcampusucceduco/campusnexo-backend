package edu.campusnexo.infrastructure.adapter.out.persistence.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

}
